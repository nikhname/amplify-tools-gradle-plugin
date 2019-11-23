import org.gradle.api.Plugin
import org.gradle.api.Project

class AmplifyTools implements Plugin<Project> {

    void apply(Project project) {
        def doesNodeExist = true

        def profile = 'default'
        def accessKeyId = null
        def secretAccessKey = null
        def region = null
        def envName = null

        project.task('verifyNode') {
            try {
                project.exec {
                    commandLine 'which', 'node'
                    standardOutput = new ByteArrayOutputStream()
                }
            } catch (e) {
                doesNodeExist = false
                println("Node is not installed. Visit https://nodejs.org/en/download/ to install it")
            }
        }

        project.task('createAmplifyApp') {
            def doesGradleConfigExist = project.file('./amplify-gradle-config.json').exists()
            if (doesNodeExist && !doesGradleConfigExist) {
                project.exec {
                    commandLine 'npx', 'amplify-app@canary', '--platform', 'android'
                }
            }
        }

        project.task('getConfig') {
            def inputConfigFile = project.file('./amplify-gradle-config.json')
            def configText = inputConfigFile.text
            def jsonSlurper = new groovy.json.JsonSlurper()
            def configJson = jsonSlurper.parseText(configText)
            profile = configJson.profile
            accessKeyId = configJson.accessKeyId
            secretAccessKey = configJson.secretAccessKeyId
            region = configJson.region
            envName = configJson.envName
        }

        project.task('modelgen') {
            doLast {
                project.exec {
                    commandLine 'amplify', 'codegen', 'model'
                }
            }
        }

        project.task('amplifyPush') {
            def AWSCLOUDFORMATIONCONFIG
            if (!accessKeyId || !secretAccessKey || !region) {
                AWSCLOUDFORMATIONCONFIG = """{\
\"configLevel\":\"project\",\
\"useProfile\":true,\
\"profileName\":\"$profile\"\
}"""
            } else {
                AWSCLOUDFORMATIONCONFIG = """{\
\"configLevel\":\"project\",\
\"useProfile\":true,\
\"profileName\":\"$profile\",\
\"accessKeyId\":\"$accessKeyId\",\
\"secretAccessKey\":\"$secretAccessKey\",\
\"region\":\"$region\"\
}"""
            }

            def AMPLIFY
            if (!envName) {
                AMPLIFY = """{\
\"envName\":\"amplify\"\
}"""
            } else {
                AMPLIFY = """{\
\"envName\":\"$envName\"\
}"""
            }

            def PROVIDERS = """{\
\"awscloudformation\":$AWSCLOUDFORMATIONCONFIG\
}"""

            doLast {
                def doesLocalEnvExist = project.file('./amplify/.config/local-env-info.json').exists()
                if (doesLocalEnvExist) {
                    project.exec {
                        commandLine 'amplify', 'push', '--yes'
                    }
                } else {
                    project.exec {
                        commandLine 'amplify', 'init', '--amplify', AMPLIFY, '--providers', PROVIDERS, '--yes'
                    }
                }
            }
        }

        project.task('addModelgenToWorkspace') {
            if (!doesGradleConfigExist) {
                //Open file
                def xml = new XmlParser().parse('./.idea/workspace.xml')
                def ProjectTypeNode = xml.component.find {
                    it.'@name' == 'RunManager'
                } as Node

                // Nested nodes for modelgen run configuration
                def configurationNode = new Node(null, 'configuration', [name: "modelgen", type:"GradleRunConfiguration", factoryName:"Gradle", nameIsGenerated:"true"])
                def externalSystemNode = new Node(configurationNode, 'ExternalSystemSettings')
                def executionOption = new Node(externalSystemNode, 'option', [name: "executionName"])
                def projectPathOption = new Node(externalSystemNode, 'option', [name: "externalProjectPath", value: "\$PROJECT_DIR\$"])
                def externalSystemIdOption = new Node(externalSystemNode, 'option', [name: "externalSystemIdString", value: "GRADLE"])
                def scriptParametersOption = new Node(externalSystemNode, 'option', [name: "scriptParameters", value: ""])
                def taskDescriptionsOption = new Node(externalSystemNode, 'option', [name: "taskDescriptions"])
                def descriptionList = new Node(taskDescriptionsOption, 'list')
                def taskNamesOption = new Node(externalSystemNode, 'option', [name: "taskNames"])
                def nameList = new Node(taskNamesOption, 'list')
                def modelgenOption = new Node(nameList, 'option', [value: "modelgen"])
                def vmOption = new Node(externalSystemNode, 'option', [name: "vmOptions", value: ""])
                def systemDebugNode = new Node(configurationNode, 'GradleScriptDebugEnabled', null, true)
                def methodNode = new Node(configurationNode, 'method', [v:"2"])

                ProjectTypeNode.append(configurationNode)

                //Save File
                def writer = new FileWriter('./.idea/workspace.xml')

                //Pretty print XML
                groovy.xml.XmlUtil.serialize(xml, writer)
            }
        }

        project.task('addAmplifyPushToWorkspace') {
            if (!doesGradleConfigExist) {
                //Open file
                def xml = new XmlParser().parse('./.idea/workspace.xml')
                def ProjectTypeNode = xml.component.find {
                    it.'@name' == 'RunManager'
                } as Node

                // Nested nodes for amplifyPush run configuration
                def configurationNode = new Node(null, 'configuration', [name: "amplifyPush", type:"GradleRunConfiguration", factoryName:"Gradle", nameIsGenerated:"true"])
                def externalSystemNode = new Node(configurationNode, 'ExternalSystemSettings')
                def executionOption = new Node(externalSystemNode, 'option', [name: "executionName"])
                def projectPathOption = new Node(externalSystemNode, 'option', [name: "externalProjectPath", value: "\$PROJECT_DIR\$"])
                def externalSystemIdOption = new Node(externalSystemNode, 'option', [name: "externalSystemIdString", value: "GRADLE"])
                def scriptParametersOption = new Node(externalSystemNode, 'option', [name: "scriptParameters", value: ""])
                def taskDescriptionsOption = new Node(externalSystemNode, 'option', [name: "taskDescriptions"])
                def descriptionList = new Node(taskDescriptionsOption, 'list')
                def taskNamesOption = new Node(externalSystemNode, 'option', [name: "taskNames"])
                def nameList = new Node(taskNamesOption, 'list')
                def amplifyPushOption = new Node(nameList, 'option', [value: "amplifyPush"])
                def vmOption = new Node(externalSystemNode, 'option', [name: "vmOptions", value: ""])
                def systemDebugNode = new Node(configurationNode, 'GradleScriptDebugEnabled', null, true)
                def methodNode = new Node(configurationNode, 'method', [v:"2"])

                ProjectTypeNode.append(configurationNode)

                //Save File
                def writer = new FileWriter('./.idea/workspace.xml')

                //Pretty print XML
                groovy.xml.XmlUtil.serialize(xml, writer)
            }
        }

        project.addModelgenToWorkspace.mustRunAfter('verifyNode')
        project.addAmplifyPushToWorkspace.mustRunAfter('addModelgenToWorkspace')
        project.createAmplifyApp.mustRunAfter('addAmplifyPushToWorkspace')
        project.getConfig.mustRunAfter('createAmplifyApp')
        project.modelgen.mustRunAfter('getConfig')
        project.amplifyPush.mustRunAfter('getConfig')
    }
}