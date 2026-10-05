{{/*
Copyright (c) 2026 devlive-community/grantforge

Licensed under the MIT License. See the LICENSE file in the
project root for full license text.
*/}}

{{/* The release's name for its objects, within Kubernetes' 63 characters. */}}
{{- define "grantforge.fullname" -}}
{{- if contains .Chart.Name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/* Labels every object carries. */}}
{{- define "grantforge.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{ include "grantforge.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/* Labels that select the server's pods. */}}
{{- define "grantforge.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/* The secret holding the database password and the optional setup token and encryption key. */}}
{{- define "grantforge.secretName" -}}
{{- default (include "grantforge.fullname" .) .Values.database.existingSecret -}}
{{- end -}}
