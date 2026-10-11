{{- define "rtp.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- /*
app.kubernetes.io/name is the service account name (platform contract; the
ExternalSecret admission policy matches it) on every pod, the migration Job's
included: mesh NetworkPolicies grant Aurora egress by that label only.
app.kubernetes.io/component tells the pods apart (service for the Deployment,
db-migration for the Job; cicd-templates 335a345), so the Service, PDB,
NetworkPolicy, topology spread and Deployment never select the Job pod.
Every value a label interpolates is quoted (guardrail 4a, template interpolation).
*/ -}}
{{- define "rtp.baseLabels" -}}
app.kubernetes.io/name: {{ .Values.serviceAccount.name | quote }}
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{- define "rtp.selectorLabels" -}}
{{ include "rtp.baseLabels" . }}
app.kubernetes.io/component: service
{{- end -}}

{{- define "rtp.labels" -}}
{{ include "rtp.selectorLabels" . }}
app: {{ .Values.serviceAccount.name | quote }}
app.kubernetes.io/part-of: fintechbankx-payments
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
fintechbankx.io/service-id: svc-pay-request-to-pay
fintechbankx.io/app: app-pay-request-to-pay
fintechbankx.io/squad: {{ required "squad is required (payments)" .Values.squad | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end -}}

{{- /* Labels of the migration hook resources (no component=service, no service-id). */ -}}
{{- define "rtp.migrationLabels" -}}
{{ include "rtp.baseLabels" . }}
app.kubernetes.io/component: db-migration
app.kubernetes.io/part-of: fintechbankx-payments
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
fintechbankx.io/squad: {{ required "squad is required (payments)" .Values.squad | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end -}}

{{- define "rtp.dbSecretName" -}}
{{ include "rtp.name" . }}-db
{{- end -}}

{{- define "rtp.migrationName" -}}
{{ include "rtp.name" . }}-db-migration
{{- end -}}

{{- /*
Secrets Manager key for an ExternalSecret remoteRef. The platform ESO role may
read only secret:<env>/*, and the admission policy fintechbankx-externalsecret-scope
admits only keys under <env>/<service account>/, so every key must be
<env>/payment-request-to-pay-service/<name>
(e.g. dev/payment-request-to-pay-service/db-app).
Usage: include "rtp.remoteKey" (list "externalSecret.remoteSecretName" .Values.externalSecret.remoteSecretName)
*/ -}}
{{- define "rtp.remoteKey" -}}
{{- $field := index . 0 -}}
{{- $key := required (printf "%s is required" $field) (index . 1) -}}
{{- if not (regexMatch "^(dev|staging|prod)/payment-request-to-pay-service/[a-z0-9-]+$" $key) -}}
{{- fail (printf "%s must be <env>/payment-request-to-pay-service/<name> (env dev, staging or prod), got %q" $field $key) -}}
{{- end -}}
{{- $key -}}
{{- end -}}

{{- /* Path of the mounted RDS CA bundle (cicd-templates 4f0f266). */ -}}
{{- define "rtp.databaseCaPath" -}}
{{- printf "%s/%s" (trimSuffix "/" .Values.databaseCa.mountPath) .Values.databaseCa.key -}}
{{- end -}}

{{- /*
Guardrail 4a, chart layer (governance round 3 answer 2b; rounds 5 and 6).
Every value that could redirect Spring's configuration, activate a profile in
the image, replace the database URL behind the sslmode=verify-full check,
change the Kafka TLS settings or switch the startup TLS assertion off is
refused at render time. Included once at the top of deployment.yaml and of
migration-job.yaml (a failure in any template stops the whole render).

The rules are the platform chart's, vendored unchanged in _fbx_helpers.tpl
(cicd-templates 6b6c317, charts/fintechbankx-service/templates/_helpers.tpl;
sha256 pinned in the README and checked by deployability.yml; its README,
"Vendoring the guard", is the contract). fbx.guard reads only .Values, so this
helper passes an adapter dict with every route this chart really renders:
  - config: .Values.config, the ConfigMap (configmap.yaml) and the migration
    Job's DB_URL / DB_USERNAME;
  - extraEnv, envFrom, extraEnvFrom: this chart renders none; the values are
    passed through so that a set value is refused rather than ignored;
  - javaToolOptions: the chart has no such value (the image's JAVA_TOOL_OPTIONS
    is the Dockerfile's); JVM option names inside config are checked by name;
  - databaseCa: always enabled here (the bundle is not optional); mountPath,
    key and configMapName are the values the templates mount, and the guard
    pins them to /etc/fintechbankx/rds-ca, global-bundle.pem and rds-ca-bundle;
  - kafka.runtime: mapped from kafka.profile (rtp.kafkaRuntime);
  - externalSecret: enabled, the service Secret's fixed key
    (SPRING_DATASOURCE_PASSWORD) as data, the migration hook Secret's fixed
    keys (DB_MIGRATION_USERNAME, DB_MIGRATION_PASSWORD) as extraData, and
    dataFrom passed through (refused when set).
Then this chart's own rules, only where fbx.guard has none (rtp.guardConfigName).
*/ -}}
{{- define "rtp.guard" -}}
{{- $root := . -}}
{{- $extraData := list -}}
{{- if .Values.migration.enabled -}}
{{- $extraData = list
      (dict "secretKey" "DB_MIGRATION_USERNAME" "property" "username" "remoteSecretName" (toString .Values.migration.remoteSecretName))
      (dict "secretKey" "DB_MIGRATION_PASSWORD" "property" "password" "remoteSecretName" (toString .Values.migration.remoteSecretName)) -}}
{{- end -}}
{{- include "fbx.guard" (dict "Values" (dict
      "config" (default dict .Values.config)
      "extraEnv" (default list .Values.extraEnv)
      "envFrom" (default list .Values.envFrom)
      "extraEnvFrom" (default list .Values.extraEnvFrom)
      "javaToolOptions" ""
      "databaseCa" (dict "enabled" true "mountPath" .Values.databaseCa.mountPath "key" .Values.databaseCa.key "configMapName" .Values.databaseCa.configMapName)
      "kafka" (dict "runtime" (include "rtp.kafkaRuntime" .))
      "externalSecret" (dict "enabled" .Values.externalSecret.enabled
                             "data" (list (dict "secretKey" "SPRING_DATASOURCE_PASSWORD" "property" "password"
                                                "remoteSecretName" (toString .Values.externalSecret.remoteSecretName)))
                             "extraData" $extraData
                             "dataFrom" (default list .Values.externalSecret.dataFrom)))) -}}
{{- if .Values.extraEnv -}}
{{- fail "extraEnv is not supported by this chart (it renders no extraEnv; Kubernetes env would win over the ConfigMap); use config.*" -}}
{{- end -}}
{{- range $key, $value := .Values.config -}}
{{- include "rtp.guardConfigName" (dict "key" $key "value" $value) -}}
{{- end -}}
{{- end -}}

{{- /*
Rule fbx.guard (6b6c317) does not have. The key is normalised the way
Spring's relaxed binding reads an environment variable (upper case, '.' and
'-' read as '_'):
  - ^SPRING_?KAFKA_?PROPERTIES_: the common Kafka client map
    (spring.kafka.properties.*). fbx.guard refuses its ssl.*, security.protocol
    and endpoint identification names but accepts the other client properties
    (MSK IAM needs sasl.*); this chart takes them all from the kafka-msk or
    kafka-strimzi profile, so sasl.jaas.config and the rest stay refused too.
The former JVM option rule (no fintechbankx or kafka in JAVA_TOOL_OPTIONS,
JDK_JAVA_OPTIONS, _JAVA_OPTIONS) is fbx.validateJvmOptions' since 6b6c317.
*/ -}}
{{- define "rtp.guardConfigName" -}}
{{- $key := toString .key -}}
{{- $n := $key | upper | replace "." "_" | replace "-" "_" -}}
{{- if regexMatch "^SPRING_?KAFKA_?PROPERTIES_" $n -}}
{{- fail (printf "config.%s is not allowed: the Kafka client properties (spring.kafka.properties.*) come from the kafka-msk or kafka-strimzi profile, not from values" $key) -}}
{{- end -}}
{{- end -}}

{{- /*
kafka.profile -> fbx.guard's kafka.runtime: exactly kafka-msk (msk, Amazon MSK
IAM over SASL_SSL) or kafka-strimzi (strimzi, Strimzi mutual TLS over SSL).
Anything else (local, a list such as kafka-msk,local, empty) is refused: the
local profile packaged in the image switches the startup TLS assertion off, and
this chart always deploys with a Kafka auth profile.
*/ -}}
{{- define "rtp.kafkaRuntime" -}}
{{- $profile := toString .Values.kafka.profile -}}
{{- if eq $profile "kafka-msk" -}}msk
{{- else if eq $profile "kafka-strimzi" -}}strimzi
{{- else -}}
{{- fail (printf "kafka.profile must be exactly kafka-msk or kafka-strimzi (a single profile; local is never deployed), got %q" $profile) -}}
{{- end -}}
{{- end -}}

{{- /* The only Spring profile the chart renders (SPRING_PROFILES_ACTIVE), through the vendored fbx.kafkaProfile. */ -}}
{{- define "rtp.kafkaProfile" -}}
{{- include "fbx.kafkaProfile" (dict "Values" (dict "kafka" (dict "runtime" (include "rtp.kafkaRuntime" .)))) -}}
{{- end -}}
