// Project W API Test Suite — Jenkins Pipeline
// Runs full TestNG suite, publishes test results and optional Allure report.
// Credentials: pass via Jenkins Credentials or environment variables (see CI.md).

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    environment {
        JAVA_VERSION = '21'
        // Override in Jenkins: base.url, test.admin.password, test.employee.password
        // Example: BASE_URL = 'https://dev.api.ekohamgroup.com'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                withMaven(maven: 'Maven 3.9') {
                    sh '''
                        OPTS="-Dbase.url=${BASE_URL:-https://dev.api.ekohamgroup.com}"
                        [ -n "${ADMIN_PASSWORD:-}" ] && OPTS="$OPTS -Dtest.admin.password=$ADMIN_PASSWORD"
                        [ -n "${EMPLOYEE_PASSWORD:-}" ] && OPTS="$OPTS -Dtest.employee.password=$EMPLOYEE_PASSWORD"
                        mvn clean test -Psuite -B -q $OPTS
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: 'target/surefire-reports/*.xml',
                          skipPublishingChecks: true
                    publishHTML target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: 'target/extent-reports',
                            reportFiles: '*.html',
                            reportName: 'Extent Report',
                            reportTitles: ''
                    ]
                }
            }
        }

        stage('Allure Report') {
            when { expression { return fileExists('target/allure-results') && '${ALLURE_PUBLISH}' == 'true' } }
            steps {
                allure includeProperties: false,
                       jdk: env.JAVA_VERSION,
                       results: [[path: 'target/allure-results']]
            }
        }
    }

    post {
        failure {
            echo 'Pipeline failed. Check test results and logs.'
        }
    }
}
