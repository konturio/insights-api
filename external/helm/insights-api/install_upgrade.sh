#!/usr/bin/env bash

set -e
CHART_PATH="./external/helm/insights-api/"
KUBECFG_FILE="./external/kubeconfig.yaml"
RELEASE="$STAGE-insights-api"
USER="$STAGE-insights-api@kontur.io"
VALUES="$CHART_PATH/values/values-$STAGE.yaml"
KUBECTL_OPTS="--kubeconfig $KUBECFG_FILE --context $STAGE"
HELM_OPTS="--kubeconfig $KUBECFG_FILE --kube-context $STAGE"

echo "STAGE=$STAGE is passed from CI job"
echo "Set secrets from the environment variables to bot user"
echo "kubectl $KUBECTL_OPTS config set clusters.$STAGE.certificate-authority-data (secret data)"

set -x

kubectl $KUBECTL_OPTS config set clusters.$STAGE.certificate-authority-data "$CERT_AUTH"
echo "kubectl $KUBECTL_OPTS config set users.$USER.client-key-data (secret data)"
kubectl $KUBECTL_OPTS config set users.$USER.client-key-data "$CLIENT_KEY"
kubectl $KUBECTL_OPTS config set users.$USER.client-certificate-data "$CLIENT_CERT"
echo "render templates to save as Gitlab job artifacts"
echo "helm $HELM_OPTS template $RELEASE $CHART_PATH -f $VALUES > pre-manifests.yaml"
# helm $HELM_OPTS template $RELEASE $CHART_PATH -f $VALUES > pre-manifests.yaml

echo "install or upgrade the release"
echo "helm $HELM_OPTS upgrade --install $RELEASE $CHART_PATH -f $VALUES -o yaml > manifests.yaml"
# helm $HELM_OPTS upgrade --install $RELEASE $CHART_PATH -f $VALUES -o yaml > manifests.yaml

# CHART_PATH='./external/helm/insights-api/' \
# && KUBECFG_FILE="./external/kubeconfig.yaml" \
# && echo "STAGE is passed from CI job" \
# && RELEASE=$STAGE-insights-api \
# && USER=$STAGE-insights-api@kontur.io \
# && VALUES="$CHART_PATH/values/values-$STAGE.yaml" \
# && KUBECTL_OPTS="--kubeconfig $KUBECFG_FILE --context $STAGE" \
# && HELM_OPTS="--kubeconfig $KUBECFG_FILE --kube-context $STAGE" \
# && echo "Set secrets from Gitlab environment variables to bot user" \
# && echo "kubectl $KUBECTL_OPTS config set clusters.$STAGE.certificate-authority-data (secret data)" \
# && kubectl $KUBECTL_OPTS config set clusters.$STAGE.certificate-authority-data $CERT_AUTH \
# && echo "kubectl $KUBECTL_OPTS config set users.$USER.client-key-data (secret data)" \
# && kubectl $KUBECTL_OPTS config set users.$USER.client-key-data $CLIENT_KEY \
# && echo "kubectl $KUBECTL_OPTS config set users.$USER.client-certificate-data (secret data)" \
# && kubectl $KUBECTL_OPTS config set users.$USER.client-certificate-data $CLIENT_CERT \
# && echo "render templates to save as Gitlab job artifacts" \
# && echo "helm $HELM_OPTS template $RELEASE $CHART_PATH -f $VALUES > pre-manifests.yaml" \
# && helm $HELM_OPTS template $RELEASE $CHART_PATH -f $VALUES > pre-manifests.yaml \
# && echo "install or upgrade the release" \
# && echo "helm $HELM_OPTS upgrade --install $RELEASE $CHART_PATH -f $VALUES -o yaml > manifests.yaml" \
# && helm $HELM_OPTS upgrade --install $RELEASE $CHART_PATH -f $VALUES -o yaml > manifests.yaml
