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
*/ -}}
{{- define "rtp.baseLabels" -}}
app.kubernetes.io/name: {{ .Values.serviceAccount.name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "rtp.selectorLabels" -}}
{{ include "rtp.baseLabels" . }}
app.kubernetes.io/component: service
{{- end -}}

{{- define "rtp.labels" -}}
{{ include "rtp.selectorLabels" . }}
app: {{ .Values.serviceAccount.name }}
app.kubernetes.io/part-of: fintechbankx-payments
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
fintechbankx.io/service-id: svc-pay-request-to-pay
fintechbankx.io/app: app-pay-request-to-pay
fintechbankx.io/squad: {{ required "squad is required (payments)" .Values.squad }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- /* Labels of the migration hook resources (no component=service, no service-id). */ -}}
{{- define "rtp.migrationLabels" -}}
{{ include "rtp.baseLabels" . }}
app.kubernetes.io/component: db-migration
app.kubernetes.io/part-of: fintechbankx-payments
app.kubernetes.io/managed-by: {{ .Release.Service }}
fintechbankx.io/squad: {{ required "squad is required (payments)" .Values.squad }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
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

{{- /*
Guardrail 4a, chart layer (governance round 3 answer 2b; rounds 5 and 6). Every
value that could redirect Spring's configuration, replace the database URL
behind the sslmode=verify-full check, or switch the startup TLS assertion off is
refused at render time. Included once, from configmap.yaml (always rendered).

Platform rules: the vendored copy of the shared chart's helpers in
_fbx-guard.tpl (cicd-templates 2caa48f, sha256 pinned in the README and checked
by deployability.yml), called through a Values adapter because the reference
chart names some values differently from this one:
  - fbx.validateJdbcUrl: config.DB_URL (and any other config value that is a
    PostgreSQL JDBC URL) is parsed the way PgJDBC reads it, not searched for a
    substring: exactly one sslmode=verify-full and exactly one
    sslrootcert=<databaseCa.mountPath>/<databaseCa.key>, lower-case TLS keys,
    no sslfactory / sslfactoryarg / sslhostnameverifier / sslpasswordcallback /
    service, no percent-encoded '=' or '&', no TLS key before the '?';
  - fbx.datasourceOverrideName on every config key: spring.datasource.*,
    spring.flyway.*, spring.liquibase.*, spring.r2dbc.* (SPRING_DATASOURCE_USERNAME
    and _PASSWORD excepted), spring.application.json, any *jdbc_url*,
    *sslfactory*, *sslhostnameverifier*, spring.config.import / location /
    additional-location / name and spring.profiles.active / include;
  - fbx.validateJvmOptions on JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS and
    _JAVA_OPTIONS values (datasource, flyway, liquibase, r2dbc, jdbc, ssl,
    application.json, spring.config, spring.profiles, '@' argument files,
    -XX:VMOptionsFile, -XX:Flags).
Adapter: databaseCa is always enabled here (the bundle is not optional), the
chart has no extraEnv or javaToolOptions value, and the ExternalSecret keys are
fixed by the templates (SPRING_DATASOURCE_PASSWORD; the migration Job's
DB_MIGRATION_* are a hook secret the service pods never mount), so those are
passed as the chart renders them.

This chart's additions (rtp.guardConfigName, rtp.kafkaProfile) follow below the
adapter; the vendored file is never edited.
*/ -}}
{{- define "rtp.guardValues" -}}
{{- $fbx := dict "Values" (dict
      "databaseCa" (dict "enabled" true "mountPath" .Values.databaseCa.mountPath "key" .Values.databaseCa.key)
      "config" (default dict .Values.config)
      "extraEnv" list
      "javaToolOptions" ""
      "externalSecret" (dict "enabled" .Values.externalSecret.enabled
                             "data" (list (dict "secretKey" "SPRING_DATASOURCE_PASSWORD"))
                             "extraData" list)) -}}
{{- include "fbx.validateDatabaseTls" $fbx -}}
{{- if .Values.extraEnv -}}
{{- fail "extraEnv is not supported by this chart (Kubernetes env wins over the ConfigMap, so DB_URL or a JVM option there would bypass the checks); use config.* and the profile values" -}}
{{- end -}}
{{- range $key, $value := .Values.config -}}
{{- include "rtp.guardConfigName" (dict "key" $key "value" $value) -}}
{{- end -}}
{{- $_ := include "rtp.kafkaProfile" . -}}
{{- end -}}

{{- /*
This chart's additions to the vendored name rules (rounds 5 and 6). The key is
normalised the way Spring's relaxed binding reads an environment variable: upper
case, '.' and '-' read as '_'. On that form:
  - ^SPRING_?(CONFIG|PROFILES|DATASOURCE|FLYWAY|LIQUIBASE|R2DBC|APPLICATION_?JSON)_?
    with any suffix, so the indexed (_0, _0_), DEFAULT, GROUP_<profile> and NAME
    spellings are refused along with the exact ones; SPRING_DATASOURCE_USERNAME
    and SPRING_DATASOURCE_PASSWORD stay allowed (the Secret materialises the
    password). A profile can activate application-local.yml inside the image,
    which switches the startup TLS assertion off: the chart's own
    SPRING_PROFILES_ACTIVE (rtp.kafkaProfile) is the only profile route left.
  - ^FINTECHBANKX_?TLS with any suffix (FINTECHBANKX_TLS_ENFORCE, _0, ...).
  - ^SPRING_?KAFKA_.*SECURITY_?PROTOCOL and ^SPRING_?KAFKA_?PROPERTIES_: the
    Kafka protocol is the kafka-msk / kafka-strimzi profile's, and the common
    client map could set security.protocol or the SSL stores past it.
  - KAFKA_SECURITY_PROTOCOL (application.yml default) must be SASL_SSL or SSL.
  - JVM option values (JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS, _JAVA_OPTIONS;
    the image entrypoint reads no JAVA_OPTS) may not mention fintechbankx or
    kafka either (-Dfintechbankx.tls.enforce=false,
    -Dspring.kafka.security.protocol=PLAINTEXT), on top of fbx.validateJvmOptions.
*/ -}}
{{- define "rtp.guardConfigName" -}}
{{- $key := toString .key -}}
{{- $n := $key | upper | replace "." "_" | replace "-" "_" -}}
{{- if and (regexMatch "^SPRING_?(CONFIG|PROFILES|DATASOURCE|FLYWAY|LIQUIBASE|R2DBC|APPLICATION_?JSON)_?" $n) (not (regexMatch "^SPRING_DATASOURCE_(USERNAME|PASSWORD)$" $n)) -}}
{{- fail (printf "config.%s is not allowed: spring.config.*, spring.profiles.*, spring.datasource.*, spring.flyway.*, spring.liquibase.*, spring.r2dbc.* and spring.application.json (any spelling, indexed forms included) can redirect Spring's configuration, activate a profile in the image or replace the database URL behind the sslmode=verify-full check; the chart sets the profile from kafka.profile and the URL from config.DB_URL" $key) -}}
{{- end -}}
{{- if regexMatch "^FINTECHBANKX_?TLS" $n -}}
{{- fail (printf "config.%s is not allowed: the chart never switches the startup TLS assertion (fintechbankx.tls.*) off" $key) -}}
{{- end -}}
{{- if or (regexMatch "^SPRING_?KAFKA_.*SECURITY_?PROTOCOL" $n) (regexMatch "^SPRING_?KAFKA_?PROPERTIES_" $n) -}}
{{- fail (printf "config.%s is not allowed: the Kafka security.protocol and client properties come from the kafka-msk or kafka-strimzi profile, not from values" $key) -}}
{{- end -}}
{{- if and (eq $n "KAFKA_SECURITY_PROTOCOL") (not (has (toString .value) (list "SASL_SSL" "SSL"))) -}}
{{- fail (printf "config.%s must be SASL_SSL (MSK IAM) or SSL (Strimzi mutual TLS), got %q" $key (toString .value)) -}}
{{- end -}}
{{- if include "fbx.isJvmOptionsName" $key -}}
{{- if regexMatch "(?i)fintechbankx|kafka" (toString .value) -}}
{{- fail (printf "config.%s must not mention fintechbankx or kafka (a -D system property would switch the startup TLS assertion off or change the Kafka security.protocol)" $key) -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- /*
The Spring profile the Deployment activates: exactly kafka-msk or kafka-strimzi.
Anything else (local, a list such as kafka-msk,local, empty) is refused: the
local profile packaged in the image switches the startup TLS assertion off.
*/ -}}
{{- define "rtp.kafkaProfile" -}}
{{- $profile := toString .Values.kafka.profile -}}
{{- if not (regexMatch "^(kafka-msk|kafka-strimzi)$" $profile) -}}
{{- fail (printf "kafka.profile must be exactly kafka-msk or kafka-strimzi (a single profile; local is never deployed), got %q" $profile) -}}
{{- end -}}
{{- $profile -}}
{{- end -}}

{{- /* Path of the mounted RDS CA bundle (cicd-templates 4f0f266). */ -}}
{{- define "rtp.databaseCaPath" -}}
{{- printf "%s/%s" (trimSuffix "/" .Values.databaseCa.mountPath) .Values.databaseCa.key -}}
{{- end -}}
