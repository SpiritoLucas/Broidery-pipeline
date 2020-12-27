// Libreria personal en github como privada
@Library('jenkins-library') _

// CONFIG DE LA APLICACION
String configFile = "/Volumes/TOSH-600/Broidery/Broidery-pipeline/config.json"
// FIN CONFIG DE LA APLICACION

String RESULTADO = ""
String COLOR = ""
String BRANCH = ""

node('master') {
    // Get configuration file.
    configuration = readJSON file: configFile
    
    // Get environmentProperties
    configuration.ENVIRONMENTS.each {
        if(it.NAME == ENVIRONMENT) {
            environmentProperties = it
            }
        }
    
    mailList = configuration.MAILS_NOTIFICATION_TO
}

pipeline {
    agent any
    environment{
        SERVER_CREDENTIALS = credentials("${configuration.CREDENTIALS}")
    }
    parameters {
        choice(name:'ENVIRONMENT', choices: ['DEV','PRD'], description: """<h4 style="color: green; text-aling: left;">Ambiente a deployar</h4> """)
    }
    stages {
        stage('DELETE LAST BUILD') {
            steps {
                sh " rm -rf *"
                sh "rm -rf /usr/local/var/www/*"
                // sh '''
                //     export PATH=/usr/local/share/supervisor:$PATH
                    sh "/usr/local/Cellar/nginx/1.19.5/bin/nginx -s stop"
                    sh "/usr/local/Cellar/nginx/1.19.5/bin/nginx"
                //     supervisorctl stop broidery
                //     nginx -s stop
                // '''
            }
        }

        stage('CLONE REPO DOTNET') {
            steps {
                sh "mkdir Broidery-backend"
                invokeJob('TEMPLATES/SOURCE_CHECKOUT_GIT',['WS':configuration.CHECKOUTS[0].WS +"/Repositorio", 'REPOSITORY': configuration.CHECKOUTS[0].SCM_URL, 'BRANCH': configuration.CHECKOUTS[0].SCM_BRANCH ])
                sh "cp -r /Users/lucas/.jenkins/workspace/TEMPLATES/SOURCE_CHECKOUT_GIT/ /Users/lucas/.jenkins/workspace/Broidery/Broidery-backend"
            }
        }
        
        stage('CLONE REPO ANGULAR') {
            steps {
                sh """mkdir "${configuration.CHECKOUTS[1].WS}" """
                invokeJob('TEMPLATES/SOURCE_CHECKOUT_GIT',['WS': configuration.CHECKOUTS[1].WS +"/Repositorio",'REPOSITORY': configuration.CHECKOUTS[1].SCM_URL, 'BRANCH': configuration.CHECKOUTS[1].SCM_BRANCH ])
                sh "cp -r /Users/lucas/.jenkins/workspace/TEMPLATES/SOURCE_CHECKOUT_GIT/ /Users/lucas/.jenkins/workspace/Broidery/Broidery-frontend"
            }
        }

        stage('FRONTEND ANALYSIS'){
            steps{
                withSonarQubeEnv('SonarQube'){
                    script {
                        def sonnarScannerHome = tool 'sonar-scanner'
                //    sh """${sonnarScannerHome}bin/sonar-scanner -Dsonar.projectKey=broideryBackend -Dsonar.projectName=Broidery-backend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.visualstudio.solution=Broidery.sln """
                    sh """${sonnarScannerHome}bin/sonar-scanner -Dsonar.projectKey=broideryFrontend -Dsonar.projectName=Broidery-frontend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[1].WS}" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[1].WS}" """
                    }
                }
            }
        }
        stage('BACKEND ANALYSIS'){
            steps{
                withSonarQubeEnv('SonarQube'){
                    script {
                        def sonnarMsBuildScannerHome = tool 'sonar-msbuild-2'
                        def mono = "/Library/Frameworks/Mono.framework/Commands/mono"
                        def sonarMsbuildExe = "/Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/SonarScanner.MSBuild.exe"
                        def msbuild = "/Library/Frameworks/Mono.framework/Commands/msbuild"
                    //sh """${sonnarMsBuildScannerHome}/sonar-scanner-4.4.0.2170/bin/sonar-scanner -Dsonar.projectKey=broideryBackend -Dsonar.projectName=Broidery-backend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.visualstudio.solution=Broidery.sln """
                //    sh """/Library/Frameworks/Mono.framework/Commands/msbuild -Dsonar.projectKey=broideryBackend -Dsonar.projectName=Broidery-backend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[0].WS}/Broidery" -Dsonar.visualstudio.solution=Broidery.sln  """
                    sh """
                          export PATH=/usr/local/share/dotnet:$PATH  
                          "${mono}" /Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/SonarScanner.MSBuild.exe begin /k:broideryBackend /d:sonar.verbose=true /v:sonar.projectVersion=1.0 /d:sonar.issuesReport.html.enable=true /d:sonar.login="eec243d0769bdca130483d5ab76d38679e4c36da"
                           dotnet build /Users/lucas/.jenkins/workspace/Broidery/Broidery-backend/Broidery/Broidery.sln
                          "${mono}" /Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/SonarScanner.MSBuild.exe end /d:sonar.login="eec243d0769bdca130483d5ab76d38679e4c36da"
                    """
                    //sh '''export PATH=/usr/local/share/dotnet:$PATH dotnet sonarscanner -Dsonar.projectKey=broideryBackend -Dsonar.projectName=Broidery-backend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources=. -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.visualstudio.solution=Broidery.sln '''
                  //  invokeJob('TEMPLATES/MSBUILD_COMPILE',['WS':configuration.CHECKOUTS[0].WS,'SQNAME':'Broidery-backend','COMPILATION':'/Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/SonarScanner.MSBuild.exe','SOLUTIONFILE':"${configuration.CHECKOUTS[0].WS}/Broidery/Broidery.sln",'COMPILATIONARGUMENTS':"/t:restore /target:publish /p:Configuration=DEV;InstallUrl=http://192.168.0.135:9000 /p:ApplicationVersion=1.0;ProductName=\"DEV\"",'RUN_SQ':true])
                   //sh  """/Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/sonar-scanner-4.4.0.217/bin/sonar-scanner -Dsonar.projectKey=broideryFrontend -Dsonar.projectName=Broidery-backend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[0].WS}" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[0].WS}" """
                    }
                }
            }
        }
        stage('BUILD FRONTEND'){
            tools { nodejs  'node' }
            steps {
            sh    """
                    cd "${configuration.CHECKOUTS[1].WS}"
                    npm i
                    npm run build
                    """
            }
        }

        stage('BUILD BACKEND') {
            when {
                expression { params.ENVIRONMENT == 'DEV' }
            }
            steps {
            sh '''
            export PATH=/usr/local/share/dotnet:$PATH
            cd Broidery-backend/Broidery/Broidery.Api.DockerStartup
            dotnet restore
            dotnet build  
            dotnet publish --configuration Release --runtime osx-x64                          
            '''
            }
        }

        stage('UPLOAD ARTIFACT') {
            steps {

                
                 sh '''#!/bin/zsh 
                 cd Broidery-backend/Broidery/Broidery.Api.DockerStartup/bin/Debug/netcoreapp3.1/
                 zip -r ./broidery-backend.zip ./publish
                 '''
                 uploadArti WS: workingDir pattern: "broidery-backend.zip" target: "DevOps/broidery-backend/"

                // cd /Users/lucas/.jenkins/workspace/Broidery/Broidery-frontend/
                // zip -r ./dist.zip ./dist 
                // mv /Users/lucas/.jenkins/workspace/Broidery/Broidery-backend/Broidery/Broidery.Api.DockerStartup/bin/Debug/netcoreapp3.1/publish.zip .
                // mv dist.zip ../
                // ls ../
                // '''
            }
        }
        
        stage('DEPLOY TO LOCAL NGINX') {
            steps {                
                sh 'cp -r Broidery-frontend/dist/Broidery-frontend/ /usr/local/var/www/ '
                sh 'cp -r Broidery-backend/Broidery/Broidery.Api.DockerStartup/bin/Release/netcoreapp3.1/osx-x64/ /usr/local/var/www/'
                sh 'date >> date.txt'
                echo 'FRONTEND PUBLICADO EN http://localhost:8081/ Y EN http://192.168.0.135:8081/'
                echo 'BACKEND PUBLICADO EN http://localhost:8082/ Y EN http://192.168.0.135:8082/'
            }
        }

    }
    post {
        always{
            script{    
                env.RESULTADO = currentBuild.currentResult
                env.BRANCH = configuration.CHECKOUTS[0].SCM_BRANCH
                
                emailext attachmentsPattern: '*.txt',
                subject: "Jenkins: " + configuration.APP + " | " + env.RESULTADO + " | "  + ENVIRONMENT + " | " + " build nº" + BUILD_NUMBER ,
                body: '''${SCRIPT, template="jenkins_dtv_3.template"}''',
                to: "${mailList}"
    
                slackNotifications()
            }
        }        
    }
}
