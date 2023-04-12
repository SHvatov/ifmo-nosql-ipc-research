#docker run \\
#    --rm \\
#    -e SONAR_HOST_URL="http://127.0.0.1:9000/"  \
#    -e SONAR_LOGIN="sqp_269464ba3e95980486b82cc3e29d5717f8c30f7" \
#    -v "./diploma-ipc/src:/usr/src" \\
#    sonarsource/sonar-scanner-cli

mvn clean verify sonar:sonar \
  -Dsonar.projectKey=kafka-ipc \
  -Dsonar.projectName='kafka-ipc' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=sqp_269464ba3e95980486b82cc3e29d5717f8c30f74
