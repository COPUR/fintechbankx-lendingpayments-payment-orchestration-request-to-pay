{{- define "rtp.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "rtp.selectorLabels" -}}
app.kubernetes.io/name: {{ include "rtp.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "rtp.labels" -}}
{{ include "rtp.selectorLabels" . }}
app: {{ include "rtp.name" . }}
app.kubernetes.io/part-of: fintechbankx-payments
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
fintechbankx.io/service-id: svc-pay-request-to-pay
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "rtp.dbSecretName" -}}
{{ include "rtp.name" . }}-db
{{- end -}}
