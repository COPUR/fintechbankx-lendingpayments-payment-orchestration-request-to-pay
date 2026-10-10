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
Environment keys values may never inject (governance round 3, answer 2b):
  - SPRING_CONFIG_IMPORT, SPRING_CONFIG_LOCATION, SPRING_CONFIG_ADDITIONAL_LOCATION:
    Spring's configuration is not redirected from values. A configtree is only
    ever a value this chart renders itself, on the fixed mount
    optional:configtree:/etc/fintechbankx/config/ (this chart renders none today).
  - SPRING_DATASOURCE_URL, SPRING_DATASOURCE_HIKARI_JDBC_URL, SPRING_FLYWAY_URL:
    the database URL is config.DB_URL, checked for sslmode=verify-full in
    configmap.yaml; these would replace it behind that check.
  - FINTECHBANKX_TLS_ENFORCE: the chart never switches the startup TLS
    assertion off (local and test configuration only).
Keys are matched whatever their case and separator (spring.config.import,
Spring_Config_Import, ...), as Spring binds them. The chart has no extraEnv;
whoever adds one passes its names through this helper as well.
Usage: include "rtp.guardEnvKeys" (list "config" (keys .Values.config))
*/ -}}
{{- define "rtp.guardEnvKeys" -}}
{{- $source := index . 0 -}}
{{- range $key := index . 1 -}}
{{- $k := $key | toString | lower | replace "." "_" | replace "-" "_" -}}
{{- if regexMatch "^spring_config_(import|location|additional_location)$" $k -}}
{{- fail (printf "%s.%s is not allowed: Spring configuration is not redirected from values (a configtree is only the chart's own optional:configtree:/etc/fintechbankx/config/)" $source $key) -}}
{{- end -}}
{{- if regexMatch "^(spring_datasource_url|spring_datasource_hikari_jdbc_url|spring_flyway_url)$" $k -}}
{{- fail (printf "%s.%s is not allowed: the database URL is config.DB_URL (sslmode=verify-full is checked there)" $source $key) -}}
{{- end -}}
{{- if eq $k "fintechbankx_tls_enforce" -}}
{{- fail (printf "%s.%s is not allowed: the chart never switches the startup TLS assertion off" $source $key) -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- /* Path of the mounted RDS CA bundle (cicd-templates 4f0f266). */ -}}
{{- define "rtp.databaseCaPath" -}}
{{- printf "%s/%s" (trimSuffix "/" .Values.databaseCa.mountPath) .Values.databaseCa.key -}}
{{- end -}}
