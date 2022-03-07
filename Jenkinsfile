pipeline {
  agent { label 'master' }

  parameters {
    booleanParam(name : 'COMPILE_MVN', defaultValue : true, description: 'COMPILE_MVN')
    booleanParam(name : 'BUILD_MVN', defaultValue : true, description: 'BUILD_MVN')
    booleanParam(name : 'BUILD_DOCKER_IMAGE', defaultValue : true, description: 'BUILD_DOCKER_IMAGE')
    booleanParam(name : 'PUSH_DOCKER_IMAGE', defaultValue : true, description: 'PUSH_DOCKER_IMAGE')
    booleanParam(name : 'DEPLOY_WORKLOAD', defaultValue : true, description: 'DEPLOY_WORKLOAD')

    //CI
    string(name : 'AWS_ACCOUNT_ID', defaultValue : '364481446289', description : 'AWS_ACCOUNT_ID')
    string(name : 'DOCKER_IMAGE_NAME', defaultValue : 'demo', description : 'DOCKER_IMAGE_NAME')
    string(name : 'DOCKER_TAG', defaultValue : '1', description : 'DOCKER_TAG')

    //CD
    string(name : 'TARGET_SVR_USER', defaultValue : 'ec2-user', description : 'TARGET_SVR_USER')
    string(name : 'TARGET_SVR_PATH', defaultValue : '/home/ec2-user', description : 'TARGET_SVR_PATH')
    string(name : 'TARGET_SVR', defaultValue : '10.0.1.61', description : 'TARGET_SVR')
  }

  environment {
    REGION = "ap-northeast-2"
    ECR_REPOSITORY = "${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-2.amazonaws.com"
    ECR_DOCKER_IMAGE = "${ECR_REPOSITORY}/${params.DOCKER_IMAGE_NAME}"
    ECR_DOCKER_TAG = "${params.DOCKER_TAG}"

    CODEBUILD_NAME = "jenkins-codebuild"
    CODEBUILD_ARTIFACT_S3_NAME = "jenkins-artifact-codebuild-s3"
    CODEBUILD_ARTIFACT_S3_KEY = "${currentBuild.number}/jenkins-codebuild"
    CODEDEPLOY_NAME = "demo-codedeploy-app"
    CODEDEPLOY_GROUP_NAME = "dev-codedeploy-group"
  }

  stages {
    stage('========== COMPILE_MVN =========='){
      //agent { label 'slave' }
      when {
        expression { return params.COMPILE_MVN }
      }
      steps{
        withMaven(maven : 'MAVEN') {
          sh 'mvn clean compile -f ./sample_app_spring/pom.xml'
        }
      }
    }

    stage('========== BUILD_MVN ==========') {
      //agent { label 'slave' }
      when {
        expression { return params.BUILD_MVN }
      }
      steps {
        withMaven(maven : 'MAVEN') {
          sh 'mvn -Dmaven.test.failure.ignore=true install -f ./sample_app_spring/pom.xml'
        }
      }
    }

    stage('========== BUILD_DOCKER_IMAGE ==========') {
      //agent { label 'slave' }
      when {
        expression { return params.BUILD_DOCKER_IMAGE }
      }
      steps {
        // jenkins 사용시 도커빌드 내용
        // dir("${env.WORKSPACE}") {         //jenkins pipeline 구동시 defualt로 세팅되는 환경변수
        //   sh 'docker build -t ${ECR_DOCKER_IMAGE}:${ECR_DOCKER_TAG} ./sample_app_spring/'   //dir로 접근하여 sh 명령 수행
        // }
        awsCodeBuild(
          credentialsType: 'keys',
          region: "${REGION}",
          projectName: "${CODEBUILD_NAME}",
          sourceControlType: 'jenkins',
          sseAlgorithm: 'AES256',
          buildSpecFile: "sample_app_spring/buildspec.yml",
          artifactTypeOverride: "S3",
          artifactNamespaceOverride: "NONE",
          artifactPackagingOverride: "ZIP",
          artifactPathOverride: "${currentBuild.number}",
          artifactLocationOverride: "${CODEBUILD_ARTIFACT_S3_NAME}"
        )
      }
      post {
        always {
          echo "post stage"
        }
      }
    }

    /* jenkins 사용시 docker hub에 이미지 푸시
    stage('========== PUSH_DOCKER_IMAGE ==========') {
      //agent { label 'slave' }
      when {
        expression { return params.PUSH_DOCKER_IMAGE }
      }
      steps {
        echo "Push Docker Image to ECR"
        sh '''
          aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REPOSITORY}
          docker push ${ECR_DOCKER_IMAGE}:${ECR_DOCKER_TAG}
        '''
      }
    }

    stage('========== DEPLOY_WORKLOAD ==========') {
      when { expression { return params.DEPLOY_WORKLOAD } }
      steps {
          sshagent (credentials: ['aws_ec2_user_ssh']) {
            // 비밀번호 입력없이 접속하는 옵션
              sh """#!/bin/bash
                  scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \
                      docker-compose.yaml \
                      ${params.TARGET_SVR_USER}@${params.TARGET_SVR}:${params.TARGET_SVR_PATH};

                  ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \
                      ${params.TARGET_SVR_USER}@${params.TARGET_SVR} \
                      'aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REPOSITORY}; \
                        export IMAGE=${ECR_DOCKER_IMAGE}; \
                        export TAG=${ECR_DOCKER_TAG}; \
                        docker-compose -f docker-compose.yaml down;
                        docker-compose -f docker-compose.yaml up -d';
              """
          }
      }
    }
    */
    stage('========== DEPLOY_WORKLOAD ==========') {
      when { expression { return params.DEPLOY_WORKLOAD } }
      steps {
        echo "Run CodeDeploy with creating deployment"
        script {
            sh'''
                aws deploy create-deployment \
                    --application-name ${CODEDEPLOY_NAME} \
                    --deployment-group-name ${CODEDEPLOY_GROUP_NAME} \
                    --region ${REGION} \
                    --s3-location bucket=${CODEBUILD_ARTIFACT_S3_NAME},bundleType=zip,key=${CODEBUILD_ARTIFACT_S3_KEY} \
                    --file-exists-behavior OVERWRITE \
                    --output json > DEPLOYMENT_ID.json
            '''
            def DEPLOYMENT_ID = sh(script: "cat DEPLOYMENT_ID.json | grep -o '\"deploymentId\": \"[^\"]*' | cut -d'\"' -f4", returnStdout: true).trim()
            echo "$DEPLOYMENT_ID"
            sh "rm -rf ./DEPLOYMENT_ID.json"
            def DEPLOYMENT_RESULT = ""
            while("$DEPLOYMENT_RESULT" != "\"Succeeded\"") {
                DEPLOYMENT_RESULT = sh(
                    script:"aws deploy get-deployment \
                                --region ${REGION} \
                                --query \"deploymentInfo.status\" \
                                --deployment-id ${DEPLOYMENT_ID}",
                    returnStdout: true
                ).trim()
                echo "$DEPLOYMENT_RESULT"
                if ("$DEPLOYMENT_RESULT" == "\"Failed\"") {
                    currentBuild.result = 'FAILURE'
                    break
                }
                sleep(10) // sleep 10s
            }
            currentBuild.result = 'SUCCESS'
        }
      }
    }
  }

  post {
    cleanup {
      echo "Post cleanup"
    }
  }
}