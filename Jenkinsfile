// Same pipeline as .github/workflows for a Jenkins multibranch job: compile, test with the
// quality gates of the pom, keep the jar and deploy main to cert (simulated).
// Needs the tool 'jdk17', Docker on the agent (Testcontainers) and the secret text credential
// 'config-server-deploy-token'.
pipeline {
  agent any
  tools {
    jdk 'jdk17'
  }
  options {
    timeout(time: 20, unit: 'MINUTES')
    disableConcurrentBuilds()
  }
  stages {
    stage('Compile') {
      steps {
        sh 'chmod +x mvnw && ./mvnw -B -ntp clean compile'
      }
    }
    stage('Tests and coverage') {
      steps {
        sh './mvnw -B -ntp verify'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
        }
      }
    }
    stage('Publish') {
      steps {
        archiveArtifacts artifacts: 'target/config-server-*.jar', fingerprint: true
      }
    }
    stage('Deploy to cert') {
      when {
        branch 'main'
      }
      steps {
        withCredentials([string(credentialsId: 'config-server-deploy-token', variable: 'DEPLOY_TOKEN')]) {
          sh 'bash devops/deploy.sh cert target/config-server-*.jar'
        }
      }
    }
  }
}
