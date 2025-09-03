const PLUGIN_ID = 'com.ahlibank.tokenization';

var fs = require('fs');
var path = require('path');
var Q = require('q'); // Use regular require
var AppGroup;
var WalletExtensionBundleID;
var WalletExtensionUIBundleID;

getProvisioningInfo()

function redError(message) {
    return new Error('"' + PLUGIN_ID + '" \x1b[1m\x1b[31m' + message + '\x1b[0m');
}

function replacePreferencesInFile(filePath, preferences) {
    var content = fs.readFileSync(filePath, 'utf8');
    for (var i = 0; i < preferences.length; i++) {
        var pref = preferences[i];
        var regexp = new RegExp(pref.key, "g");
        content = content.replace(regexp, pref.value);
    }
    fs.writeFileSync(filePath, content);
}

// Determine the full path to the app's xcode project file.
function findXCodeproject(context, callback) {
    fs.readdir(iosFolder(context), function(err, data) {
        var projectFolder;
        var projectName;
        // Find the project folder by looking for *.xcodeproj
        if (data && data.length) {
            data.forEach(function(folder) {
                if (folder.match(/\.xcodeproj$/)) {
                    projectFolder = path.join(iosFolder(context), folder);
                    projectName = path.basename(folder, '.xcodeproj');
                }
            });
        }

        if (!projectFolder || !projectName) {
            throw redError('Could not find an .xcodeproj folder in: ' + iosFolder(context));
        }

        if (err) {
            throw redError(err);
        }

        callback(projectFolder, projectName);
    });
}

// Determine the full path to the ios platform
function iosFolder(context) {
    return context.opts.cordova.project ?
        context.opts.cordova.project.root :
        path.join('platforms','ios');
}

function getPreferenceValue(configXml, name) {
    var value = configXml.match(new RegExp('name="' + name + '" value="(.*?)"', "i"));
    if (value && value[1]) {
        return value[1];
    } else {
        return null;
    }
}

function getCordovaParameter(configXml, variableName) {
    var variable;
    var arg = process.argv.filter(function(arg) {
        return arg.indexOf(variableName + '=') == 0;
    });
    if (arg.length >= 1) {
        variable = arg[0].split('=')[1];
    } else {
        variable = getPreferenceValue(configXml, variableName);
    }
    return variable;
}

function parsePbxProject(context, pbxProjectPath) {
    var xcode = require('xcode'); // Use regular require
    console.log('    Parsing existing project at location: ' + pbxProjectPath + '...');
    var pbxProject;
    if (context.opts.cordova.project) {
        pbxProject = context.opts.cordova.project.parseProjectFile(context.opts.projectRoot).xcode;
    } else {
        pbxProject = xcode.project(pbxProjectPath);
        pbxProject.parseSync();
    }
    return pbxProject;
}

function forEachWalletExtensionUIFile(context, callback) {
    var WalletExtensionUIFolder = path.join(iosFolder(context), 'WalletExtensionUI');
    fs.readdirSync(WalletExtensionUIFolder).forEach(function(name) {
        // Ignore junk files like .DS_Store
        if (!/^\..*/.test(name)) {
            callback({
                name: name,
                path: path.join(WalletExtensionUIFolder, name),
                extension: path.extname(name)
            });
        }
    });
}

function projectPlistPath(context, projectName) {
    return path.join(iosFolder(context), projectName, projectName + '-Info.plist');
}

function projectPlistJson(context, projectName) {
    var plist = require('plist');
    var path = projectPlistPath(context, projectName);
    return plist.parse(fs.readFileSync(path, 'utf8'));
}

function getPreferences(context, configXml, projectName) {
    var plist = projectPlistJson(context, projectName);
    var group = AppGroup;
    if (getCordovaParameter(configXml, 'GROUP_IDENTIFIER') !== "") {
        group = getCordovaParameter(configXml, 'IOS_GROUP_IDENTIFIER');
    }
    return [{
        key: '__DISPLAY_NAME__',
        value: projectName
    }, {
        key: '__BUNDLE_IDENTIFIER__',
        value: WalletExtensionUIBundleID
    }, {
        key: '__GROUP_IDENTIFIER__',
        value: group
    }, {
        key: '__BUNDLE_SHORT_VERSION_STRING__',
        value: plist.CFBundleShortVersionString
    }, {
        key: '__BUNDLE_VERSION__',
        value: plist.CFBundleVersion
    }];
}

// Return the list of files in the WalletExtensionUI project, organized by type
function getWalletExtensionUIFiles(context) {
    var files = {
        source: [],
        plist: [],
        resource: []
    };
    var FILE_TYPES = {
        '.swift': 'source',
        '.plist': 'plist'
    };
    forEachWalletExtensionUIFile(context, function(file) {
        var fileType = FILE_TYPES[file.extension] || 'resource';
        files[fileType].push(file);
    });
    return files;
}

function printWalletExtensionUIFiles(files) {
    console.log('    Found following files in your WalletExtensionUI folder:');
    console.log('    Source files:');
    files.source.forEach(function(file) {
        console.log('     - ', file.name);
    });

    console.log('    Plist files:');
    files.plist.forEach(function(file) {
        console.log('     - ', file.name);
    });

    console.log('    Resource files:');
    files.resource.forEach(function(file) {
        console.log('     - ', file.name);
    });
}

console.log('Adding target "' + PLUGIN_ID + '/WalletExtensionUI" to XCode project');

module.exports = function(context) {
    var deferral = Q.defer();

    var configXml = fs.readFileSync(path.join(context.opts.projectRoot, 'config.xml'), 'utf-8');
    if (configXml) {
        configXml = configXml.substring(configXml.indexOf('<'));
    }

    findXCodeproject(context, function(projectFolder, projectName) {
        console.log('  - Folder containing your iOS project: ' + iosFolder(context));

        var pbxProjectPath = path.join(projectFolder, 'project.pbxproj');
        var pbxProject = parsePbxProject(context, pbxProjectPath);

        var files = getWalletExtensionUIFiles(context);
        // printWalletExtensionUIFiles(files);

        var preferences = getPreferences(context, configXml, projectName);
        files.plist.concat(files.source).forEach(function(file) {
            replacePreferencesInFile(file.path, preferences);
            // console.log('    Successfully updated ' + file.name);
        });

        // Find if the project already contains the target and group
        var target = pbxProject.pbxTargetByName('WalletExtensionUI');
        if (target) {
            console.log('WalletExtensionUI target already exists.');
        }

        if (!target) {
            // Add PBXNativeTarget to the project
            target = pbxProject.addTarget('WalletExtensionUI', 'app_extension', 'WalletExtensionUI');

            // Add a new PBXSourcesBuildPhase for our IntentViewController
            // (we can't add it to the existing one because an extension is kind of an extra app)
            pbxProject.addBuildPhase([], 'PBXSourcesBuildPhase', 'Sources', target.uuid);

            // Add a new PBXResourcesBuildPhase for the Resources used by the WalletExtensionUI
            // (MainInterface.storyboard)
            pbxProject.addBuildPhase([], 'PBXResourcesBuildPhase', 'Resources', target.uuid);
        }

        // Create a separate PBXGroup for the WalletExtensionUIs files, name has to be unique and path must be in quotation marks
        var pbxGroupKey = pbxProject.findPBXGroupKey({
            name: 'WalletExtensionUI'
        });
        if (pbxProject) {
            console.log('WalletExtensionUI group already exists.');
        }
        if (!pbxGroupKey) {
            pbxGroupKey = pbxProject.pbxCreateGroup('WalletExtensionUI', 'WalletExtensionUI');

            // Add the PbxGroup to cordovas "CustomTemplate"-group
            var customTemplateKey = pbxProject.findPBXGroupKey({
                name: 'CustomTemplate'
            });
            pbxProject.addToPbxGroup(pbxGroupKey, customTemplateKey);
        }

        //-------Target library setup---------//
        // Add a new PBXFrameworksBuildPhase for the Frameworks used by the widget (NotificationCenter.framework, libCordova.a)
        var frameworksBuildPhase = pbxProject.addBuildPhase([],'PBXFrameworksBuildPhase','Frameworks',target.uuid);
        if (frameworksBuildPhase) {
            console.log('Successfully added PBXFrameworksBuildPhase!', 'info');
        }

        // Add the frameworks needed by our widget, add them to the existing Frameworks PbxGroup and PBXFrameworksBuildPhase
        pbxProject.addFramework('IntentsUI.framework', {target: target.uuid});
        pbxProject.addFramework('PassKit.framework', {target: target.uuid});
        pbxProject.addFramework('UIKit.framework', {target: target.uuid});
        //-------------------------------------

        // Add files which are not part of any build phase (config)
        files.plist.forEach(function(file) {
            pbxProject.addFile(file.name, pbxGroupKey);
        });

        // Add source files to our PbxGroup and our newly created PBXSourcesBuildPhase
        files.source.forEach(function(file) {
            pbxProject.addSourceFile(file.name, {
                target: target.uuid
            }, pbxGroupKey);
        });

        //  Add the resource file and include it into the targest PbxResourcesBuildPhase and PbxGroup
        files.resource.forEach(function(file) {
            pbxProject.addResourceFile(file.name, {
                target: target.uuid
            }, pbxGroupKey);
        });

        fs.writeFileSync(pbxProjectPath, pbxProject.writeSync());
        console.log('Added WalletExtensionUI to XCode project');

        deferral.resolve();
    });

    return deferral.promise;
};

function getProvisioningInfo() {
    const jsonFilePath = path.join(process.cwd(), 'provisioning_info.json');
    if (!fs.existsSync(jsonFilePath)) {
        console.error(`🚨 Error: provisioning_info.json not found at: ${jsonFilePath}`);
        return null;
    }
    const jsonContent = fs.readFileSync(jsonFilePath, 'utf8');

    var ProvisioningInfo = JSON.parse(jsonContent);
    AppGroup = ProvisioningInfo.appGroup;
    WalletExtensionBundleID = ProvisioningInfo.thirdTarget.id;
    WalletExtensionUIBundleID = ProvisioningInfo.secondTarget.id;
}