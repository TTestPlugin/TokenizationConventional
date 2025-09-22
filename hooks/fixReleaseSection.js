const fs = require('fs');
const path = require('path');

function getProjectName() {
    const config = fs.readFileSync('config.xml').toString();
    const parseString = require('xml2js').parseString;
    let name;
    parseString(config, function (err, result) {
        if (err) {
            console.error("Error parsing config.xml:", err.message);
            return;
        }
        name = result.widget.name.toString().trim();
    });
    return name || null;
}

const projectName = getProjectName() + '.xcodeproj';
const projectPath = path.join('platforms', 'ios', projectName, 'project.pbxproj');
const projectPath2 = path.join('project2.pbxproj');

function getProvisioningInfo() {
    const jsonFilePath = path.join(process.cwd(), 'provisioning_info.json');
    if (!fs.existsSync(jsonFilePath)) {
        console.error(`🚨 Error: provisioning_info.json not found at: ${jsonFilePath}`);
        return null;
    }
    const jsonContent = fs.readFileSync(jsonFilePath, 'utf8');
    return JSON.parse(jsonContent);
}

function updateReleaseSections(targets, projectPath) {
    console.log("👉 Reading Xcode project at:", projectPath);
    var pbxprojContent = fs.readFileSync(projectPath, 'utf8');

    targets.forEach(target => {
        
        const { ppName, id, ppUUID, teamID } = target;
        console.log(`🔍 Processing Release section for target: ${ppName}`);

        const releaseRegex = new RegExp(
            `name\\s*=\\s*Release;[^}]*?INFOPLIST_FILE\\s*=\\s*"${ppName}/${ppName}-Info\\.plist";`,
            's'
        );

        const match = pbxprojContent.match(releaseRegex);
        if (!match) {
            console.warn(`⚠️ Release section not found for target: ${ppName}`);
            return;
        }

        console.log(`✅ Found Release section for target: ${ppName}`);
        console.log(`🔧 Original Release section for target ${ppName}:`, match[0]);

        var toBeReplaced = match[0];

        const replacement = `
                PRODUCT_BUNDLE_IDENTIFIER = ${id};
                PROVISIONING_PROFILE = ${ppUUID};
                DEVELOPMENT_TEAM = ${teamID};
                CODE_SIGN_IDENTITY = "iPhone Distribution";`;

        console.log(`🔧 Preparing to update Release section for target: ${ppName}`);
        //console.log(`🔧 Replacement string:`, replacement);

        //console.log(`🔧 Before replacement for target ${ppName}:`, pbxprojContent.match(releaseRegex));
        pbxprojContent = pbxprojContent.replace(toBeReplaced, toBeReplaced + replacement);
        //pbxprojContent = pbxprojContent.replace("63W4GSEJ66", "TESTE");
        //console.log(`🔧 After replacement for target ${ppName}:`, pbxprojContent.match(releaseRegex));

    });

    fs.writeFileSync(projectPath2, pbxprojContent, { encoding: 'utf8', flag: 'w' });
    fs.writeFileSync(projectPath, pbxprojContent, { encoding: 'utf8', flag: 'w' });
        console.log(`✅ Successfully wrote updated content to file: ${projectPath}`);    

    /*const updatedContent = fs.readFileSync(projectPath, 'utf8');
    console.log(`🔍 Re-reading updated content for verification...`);

    targets.forEach(target => {
        const verifyRegex = new RegExp(
            `name\\s*=\\s*Release;[^}]*?INFOPLIST_FILE\\s*=\\s*"${target.ppName}/${target.ppName}-Info\\.plist";[^}]*?PRODUCT_BUNDLE_IDENTIFIER\\s*=\\s*${target.id};[^}]*?PROVISIONING_PROFILE\\s*=\\s*${target.ppUUID};[^}]*?DEVELOPMENT_TEAM\\s*=\\s*${target.teamID};[^}]*?CODE_SIGN_IDENTITY\\s*=\\s*"iPhone Distribution";`,
            's'
        );
        const verifyMatch = updatedContent.match(verifyRegex);
        console.log(`🔍 Using regex for verification: ${verifyRegex}`);
        if (verifyMatch) {
            console.log(`✅ Verified updated Release section for target: ${target.ppName}`);
            console.log(`🔧 Verified content:`, verifyMatch[0]);
        } else {
            console.warn(`⚠️ Could not verify Release section for target: ${target.ppName}`);
        }
    });*/
}

function enhancePbxproj() {
    const provisioningInfo = getProvisioningInfo();
    if (!provisioningInfo) {
        console.error("🚨 Error: Could not load provisioning information.");
        return;
    }

    const { secondTarget, thirdTarget, teamID } = provisioningInfo;
    const targets = [
        {
            ppName: secondTarget.ppName,
            id: secondTarget.id,
            ppUUID: secondTarget.ppUUID,
            teamID: teamID,
        },
        {
            ppName: thirdTarget.ppName,
            id: thirdTarget.id,
            ppUUID: thirdTarget.ppUUID,
            teamID: teamID,
        },
    ];

    updateReleaseSections(targets, projectPath);
}

enhancePbxproj();
