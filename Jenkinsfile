pipeline {
  agent any

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
  }

  stages {
    stage('========== COMPILE_MVN =========='){
      when {
        expression { return params.COMPILE_MVN }
      }
      steps{
        withMaven {
          sh 'mvn clean compile -f ./pom.xml'
        }
      }
    }

    stage('========== BUILD_MVN ==========') {
      when {
        expression { return params.BUILD_MVN }
      }
      steps {
        withMaven {
          sh 'mvn -Dmaven.test.failure.ignore=true install -f ./pom.xml'
        }
      }
    }

    stage('========== BUILD_DOCKER_IMAGE ==========') {
      when {
        expression { return params.BUILD_DOCKER_IMAGE }
      }
      steps {
        dir("${env.WORKSPACE}") {         //jenkins pipeline 구동시 defualt로 세팅되는 환경변수
          sh 'docker build -t ${ECR_DOCKER_IMAGE}:${ECR_DOCKER_TAG} ./'   //dir로 접근하여 sh 명령 수행
        }
      }
      post {
        always {
          echo "post stage"
        }
      }
    }

    // jenkins 사용시 docker hub에 이미지 푸시
    stage('========== PUSH_DOCKER_IMAGE ==========') {
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
  }

  post {
    cleanup {
      echo "Post cleanup"
    }
  }
}