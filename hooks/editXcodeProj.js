const fs = require('fs');
const path = require('path');
const parseString = require('xml2js').parseString;
const plist = require('plist');

function getProjectName() {
    return new Promise((resolve, reject) => {
        console.log('Reading project name from config.xml...');
        fs.readFile('config.xml', (err, data) => {
            if (err) {
                console.error('Error reading config.xml:', err.message);
                return reject(err);
            }

            parseString(data.toString(), (err, result) => {
                if (err) {
                    console.error('Error parsing config.xml:', err.message);
                    return reject(err);
                }

                let name = result.widget.name.toString().trim();
                console.log('Project name:', name);
                resolve(name || null);
            });
        });
    });
}

function getProvisioningInfo() {
    return new Promise((resolve, reject) => {
        const jsonFilePath = path.join(process.cwd(), 'provisioning_info.json');
        console.log('Reading provisioning information from:', jsonFilePath);

        fs.readFile(jsonFilePath, 'utf8', (err, data) => {
            if (err) {
                console.error('🚨 Error reading provisioning info JSON file:', err.message);
                return reject(`Error reading provisioning info JSON file: ${err.message}`);
            }

            try {
                const provisioningInfo = JSON.parse(data);

                const {
                    teamID,
                    firstTarget,
                    secondTarget,
                    thirdTarget,
                } = provisioningInfo;

                if (!teamID || !firstTarget || !secondTarget || !thirdTarget) {
                    console.error('🚨 Missing required provisioning information.');
                    return reject('Missing required provisioning information.');
                }

                console.log('👉 Team ID:', teamID);
                console.log('👉 First target:', firstTarget);
                console.log('👉 Second target:', secondTarget);
                console.log('👉 Third target:', thirdTarget);

                resolve({ teamID, firstTarget, secondTarget, thirdTarget });
            } catch (err) {
                console.error('🚨 Error parsing provisioning info JSON file:', err.message);
                reject(`Error parsing provisioning info JSON file: ${err.message}`);
            }
        });
    });
}

function backupPbxProj(pbxprojPath, backupName) {
    return new Promise((resolve, reject) => {
        const backupPath = path.join(path.dirname(pbxprojPath), backupName);
        console.log(`👉 Creating backup of project.pbxproj at: ${backupPath}`);

        fs.copyFile(pbxprojPath, backupPath, (err) => {
            if (err) {
                console.error(`🚨 Error creating backup file: ${err.message}`);
                return reject(`Error creating backup file: ${err.message}`);
            }
            console.log(`✅ Backup successfully created at ${backupPath}`);
            resolve();
        });
    });
}

function updatePbxProj(pbxprojPath, teamID, targets, codeSignIdentity) {
    return new Promise((resolve, reject) => {
        console.log('👉 Updating project.pbxproj at:', pbxprojPath);

        fs.readFile(pbxprojPath, 'utf8', (err, data) => {
            if (err) {
                console.error('🚨 Error reading project.pbxproj:', err.message);
                return reject(err);
            }

            let updatedPbxproj = data;

            // Update each target
            targets.forEach(target => {
                const { id, ppUUID, ppName } = target;

                // Ensure PRODUCT_NAME is wrapped in double quotes
                const productNamePattern = new RegExp(`PRODUCT_NAME\\s*=\\s*${id}\\s*;`, 'g');
                updatedPbxproj = updatedPbxproj.replace(productNamePattern, () => {
                    return `PRODUCT_NAME = "${id}";`;
                });

                // Ensure PROVISIONING_PROFILE is wrapped in double quotes
                const provisioningProfilePattern = new RegExp(`PROVISIONING_PROFILE\\s*=\\s*${ppUUID};`, 'g');
                updatedPbxproj = updatedPbxproj.replace(provisioningProfilePattern, () => {
                    return `PROVISIONING_PROFILE = "${ppUUID}";`;
                });

                // Add SWIFT_VERSION for the target
                const swiftVersionPattern = new RegExp(`PRODUCT_NAME\\s*=\\s*"\\$\$begin:math:text$TARGET_NAME\\$end:math:text$";`, 'g');
                updatedPbxproj = updatedPbxproj.replace(swiftVersionPattern, (match) => {
                    return `${match}\n\t\t\t\tSWIFT_VERSION = 5;`;
                });
            });

            // Regex pattern to find and replace DEVELOPMENT_TEAM = "" with dynamic teamID
            const devTeamPattern = /DEVELOPMENT_TEAM\s*=\s*"";/g;
            updatedPbxproj = updatedPbxproj.replace(devTeamPattern, `DEVELOPMENT_TEAM = "${teamID}";`);

            // Add the step to update the LD_RUNPATH_SEARCH_PATHS for each target
            targets.forEach(target => {
                updatedPbxproj = updatedPbxproj.replace(
                    new RegExp(`(\\{[^}]*?PRODUCT_NAME\\s*=\\s*${target.id};[^}]*?LD_RUNPATH_SEARCH_PATHS\\s*=\\s*"@executable_path/Frameworks";|\\{[^}]*?LD_RUNPATH_SEARCH_PATHS\\s*=\\s*"@executable_path/Frameworks";[^}]*?PRODUCT_NAME\\s*=\\s*${target.id};)`, 'gs'),
                    function (match) {
                        return match.replace('LD_RUNPATH_SEARCH_PATHS = "@executable_path/Frameworks";', 'LD_RUNPATH_SEARCH_PATHS = "@executable_path/../../Frameworks";');
                    }
                );
            });

            fs.writeFile(pbxprojPath, updatedPbxproj, 'utf8', (err) => {
                if (err) {
                    console.error('🚨 Error writing updated project.pbxproj:', err.message);
                    return reject(err);
                }
                console.log(`✅ Successfully updated the project.pbxproj file with teamID: ${teamID} and CODE_SIGN_IDENTITY: ${codeSignIdentity}`);
                resolve();
            });
        });
    });
}

function editXcodeProj(context) {
    console.log("👉 context.cmdLine: " + context.cmdLine);
    let buildMode = 'Debug';
    if (context.cmdLine.toLowerCase().indexOf('release') >= 0) {
        buildMode = 'Release';
    }
    console.log(`👉 Build mode detected: ${buildMode}`);

    return getProjectName()
        .then((projectName) => {
            if (!projectName) {
                throw new Error('🚨 Project name not found in config.xml.');
            }

            return getProvisioningInfo().then(({ teamID, firstTarget, secondTarget, thirdTarget }) => {
                const xcodeprojPath = path.join('platforms', 'ios', `${projectName}.xcodeproj`, 'project.pbxproj');
                console.log('👉 Resolved path to project.pbxproj:', xcodeprojPath);

                if (!fs.existsSync(xcodeprojPath)) {
                    console.error('🚨 The path to project.pbxproj was not found:', xcodeprojPath);
                    throw new Error(`The path to project.pbxproj was not found: ${xcodeprojPath}`);
                }

                let codeSignIdentity = '';
                if (buildMode === 'Debug') {
                    codeSignIdentity = 'iPhone Developer';
                } else if (buildMode === 'Release') {
                    codeSignIdentity = 'iPhone Distribution';
                } else {
                    throw new Error(`🚨 Unknown build mode: ${buildMode}`);
                }

                console.log(`👉 Setting CODE_SIGN_IDENTITY to: ${codeSignIdentity}`);

                const targets = [firstTarget, secondTarget, thirdTarget];

                return backupPbxProj(xcodeprojPath, 'project-before-edit.pbxproj')
                    .then(() => updatePbxProj(xcodeprojPath, teamID, targets, codeSignIdentity))
                    .then(() => backupPbxProj(xcodeprojPath, 'project-after_hook.pbxproj'));
            });
        })
        .catch((err) => {
            console.error('🚨 Error during the editXcodeProj process:', err.message);
            throw err;
        });
}

module.exports = function (context) {
    console.log("👉 context.cmdLine: " + context.cmdLine);
    return editXcodeProj(context);
};
