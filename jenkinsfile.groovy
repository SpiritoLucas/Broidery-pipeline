// Libreria personal en github como privada
@Library('jenkins-library') _

// CONFIG DE LA APLICACION
String configFile = "/Volumes/TOSH-600/Broidery/Broidery-pipeline/config.json"
// FIN CONFIG DE LA APLICACION

String RESULTADO = ""
String COLOR = ""
//String BRANCH = ""
String FECHAHORA = ""
Date date = new Date()
String datePart = date.format("yyyyMMdd")
String timePart = date.format("HHmm")
FECHAHORA = datePart + timePart

try { ACTION } catch (MissingPropertyException) { ACTION = '' }
try { ENVIRONMENT } catch (MissingPropertyException) { ENVIRONMENT = '' }
try { BRANCH } catch (MissingPropertyException) { BRANCH = '' }
//try { COMMITED_REVISION } catch (MissingPropertyException) { COMMITED_REVISION = '' }
//try { SCRIPTS_SQL } catch (MissingPropertyException) { SCRIPTS_SQL = '' }
//try { SCRIPTS_ORACLE } catch (MissingPropertyException) { SCRIPTS_ORACLE = '' }
//try { ARTIFACT } catch (MissingPropertyException) { ARTIFACT = '' }

node('master') {
    // Get configuration file.
    configuration = readJSON file: configFile
    
    // Get environmentProperties
    configuration.ENVIRONMENT.each {
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
        when { expression { ACTION == 'DEPLOY' } 
            anyOf {
                expression { LAYER == 'COMPLETE' }
                expression { LAYER == 'ONLY_APP' }
            }
        }
            steps {
                sh "echo ${configuration.WS2}"
                sh " rm -rf *"
                sh "rm -rf /usr/local/var/www/*"
            }
        }

        stage('CLONE REPO DOTNET') {
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {
                sh "mkdir Broidery-backend"
                invokeJob('TEMPLATES/SOURCE_CHECKOUT_GIT',['WS':configuration.CHECKOUTS[0].WS +"/Repositorio", 'REPOSITORY': configuration.CHECKOUTS[0].SCM_URL, 'BRANCH': configuration.CHECKOUTS[0].SCM_BRANCH ])
                sh "cp -r /Users/lucas/.jenkins/workspace/TEMPLATES/SOURCE_CHECKOUT_GIT/ /Users/lucas/.jenkins/workspace/Broidery/Broidery-backend"
            }
        }
        
        stage('CLONE REPO ANGULAR') {
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {
                sh """mkdir "${configuration.CHECKOUTS[1].WS}" """
                invokeJob('TEMPLATES/SOURCE_CHECKOUT_GIT',['WS': configuration.CHECKOUTS[1].WS +"/Repositorio",'REPOSITORY': configuration.CHECKOUTS[1].SCM_URL, 'BRANCH': "${env.BRANCH}" ])
                sh "cp -r /Users/lucas/.jenkins/workspace/TEMPLATES/SOURCE_CHECKOUT_GIT/ /Users/lucas/.jenkins/workspace/Broidery/Broidery-frontend"
                gitChangelog returnType: 'CONTEXT'
            }
        }

        stage('FRONTEND ANALYSIS'){
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps{
                withSonarQubeEnv('SonarQube'){
                    script {
                        def sonnarScannerHome = tool 'sonar-scanner'
                    sh """${sonnarScannerHome}bin/sonar-scanner -Dsonar.projectKey=broideryFrontend -Dsonar.projectName=Broidery-frontend -Dsonar.projectVersion=1.0 -Dsonar.sourceEncoding=UTF-8 -Dsonar.visualstudio.enable=true -Dsonar.sources="${configuration.CHECKOUTS[1].WS}" -Dsonar.issuesReport.html.enable=true -Dsonar.host.url=http://192.168.0.135:9000 -Dsonar.projectBaseDir="${configuration.CHECKOUTS[1].WS}" """
                    }
                }
            }
        }
        stage('BACKEND ANALYSIS'){
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps{
                withSonarQubeEnv('SonarQube'){
                    script {
                        def mono = "/Library/Frameworks/Mono.framework/Commands/mono"
                        def sonarMsbuildExe = "/Users/lucas/.jenkins/tools/hudson.plugins.sonar.MsBuildSQRunnerInstallation/sonar-msbuild/SonarScanner.MSBuild.exe"
                        def solutionFile = "/Users/lucas/.jenkins/workspace/Broidery/Broidery-backend/Broidery/Broidery.sln"
                    sh """
                          export PATH=/usr/local/share/dotnet:$PATH  
                          "${mono}" "${sonarMsbuildExe}" begin /k:broideryBackend /v:sonar.projectVersion=1.0 /d:sonar.issuesReport.html.enable=true /d:sonar.login="eec243d0769bdca130483d5ab76d38679e4c36da"
                           dotnet build "${solutionFile}"
                          "${mono}" "${sonarMsbuildExe}" end /d:sonar.login="eec243d0769bdca130483d5ab76d38679e4c36da"
                       """ 
                        timeout(time: 1, unit: 'HOURS') { 
                            def qg = waitForQualityGate() 
                            if (qg.status != 'OK') {
                              error "Pipeline aborted due to quality gate failure: ${qg.status}"
                            }
                        }
                    }
                }
            }
        }
                          
        stage('BUILD FRONTEND'){
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
         //   tools { nodejs  'node' }
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
                expression { ACTION == 'DEPLOY' }
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {
            sh '''
                export PATH=/usr/local/share/dotnet:$PATH
                cd Broidery-backend/Broidery/Broidery.Api.DockerStartup
                dotnet publish --configuration Release --runtime osx-x64                          
               '''
            }
        }

        stage('BUILD PROD BACKEND') {
            when {
                expression { params.ENVIRONMENT == 'PRD' }
                expression { ACTION == 'DEPLOY' }
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {
            sh '''
                export PATH=/usr/local/share/dotnet:$PATH
                cd Broidery-backend/Broidery/Broidery.Api.DockerStartup
                dotnet publish --configuration Release                          
               '''
            }
        }

        stage('UPLOAD ARTIFACTS') {
            when { expression { ACTION == 'DEPLOY' } 
                   expression { params.ENVIRONMENT == 'DEV' }
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {
                 zip archive: true, dir: 'Broidery-backend/Broidery/Broidery.Api.DockerStartup/bin/Release/netcoreapp3.1/osx-x64/', glob: '', overwrite: false, zipFile: """broidery-backend.zip"""
               // uploadArti WS: '.' , pattern: "broidery-backend.zip", target: "DevOps/${ENVIRONMENT}/Backend/${FECHAHORA}/"
                 zip archive: true, dir: 'Broidery-frontend/dist/Broidery-frontend/', glob: '', overwrite: false, zipFile: "broidery-frontend.zip"
               // uploadArti WS: '.' , pattern: "broidery-frontend.zip", target: "DevOps/${ENVIRONMENT}/Frontend/${FECHAHORA}/"
            }
        }
        
        stage('DEPLOY TO LOCAL NGINX') {
            when { expression { ACTION == 'DEPLOY' } 
                   expression { params.ENVIRONMENT == 'DEV' }
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps {                
                sh 'cp -r Broidery-frontend/dist/Broidery-frontend/ /usr/local/var/www/ '
                sh 'cp -r Broidery-backend/Broidery/Broidery.Api.DockerStartup/bin/Release/netcoreapp3.1/osx-x64/ /usr/local/var/www/'
                sh 'date >> date.txt'
                echo 'FRONTEND PUBLICADO EN http://localhost:8081/ Y EN http://192.168.0.135:8081/'
                echo 'BACKEND PUBLICADO EN http://localhost:8082/ Y EN http://192.168.0.135:8082/'
            }
        }
        stage('RESTART APP SERVICE'){
            when { expression { ACTION == 'DEPLOY' } 
                anyOf {
                    expression { LAYER == 'COMPLETE' }
                    expression { LAYER == 'ONLY_APP' }
                }
            }
            steps{
                sh '/usr/local/bin/supervisorctl restart broidery'
                sh "/usr/local/Cellar/nginx/1.19.5/bin/nginx -s reload"
            }
        }
    }
    post {
        always{
            script{    
                env.RESULTADO = currentBuild.currentResult
                
                emailext attachmentsPattern: '*.txt',
                subject: "Jenkins: " + configuration.APP + " | " + env.RESULTADO + " | "  + ENVIRONMENT + " | " + " build nº" + BUILD_NUMBER ,
                body: '''${SCRIPT, template="jenkins_dtv_3.template"}''',
                to: "${mailList}"
    
                slackNotifications()
                
              /*dir('/Users/lucas/.jenkins/workspace/Broidery/Broidery-frontend'){
                        def changelogString = gitChangelog returnType: 'STRING',
                     
                      from: [type: 'REF', value: '05-sh-Add_about_us_component_content'],
                      to: [type: 'REF', value: env.BRANCH ],
                      template: """
                      <h1> Git Changelog changelog </h1>
                    
                        <p>
                        Changelog of Git Changelog.
                        </p>
                        
                        {{#tags}}
                        <h2> {{name}} </h2>
                         {{#issues}}
                          {{#hasIssue}}
                           {{#hasLink}}
                        <h2> {{name}} <a href="{{link}}">{{issue}}</a> {{title}} </h2>
                           {{/hasLink}}
                           {{^hasLink}}
                        <h2> {{name}} {{issue}} {{title}} </h2>
                           {{/hasLink}}
                          {{/hasIssue}}
                          {{^hasIssue}}
                        <h2> {{name}} </h2>
                          {{/hasIssue}}
                        
                        
                           {{#commits}}
                        <p>
                        <h3>{{{messageTitle}}}</h3>
                        
                        {{#messageBodyItems}}
                         <li> {{.}}</li> 
                        {{/messageBodyItems}}
                        </p>
                        
                        
                          {{/commits}}
                        
                         {{/issues}}
                        {{/tags}}
                          """
                          
                          currentBuild.description = changelogString
                }*/
            }
        }
    }
}
