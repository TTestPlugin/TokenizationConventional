const fs = require('fs');
const path = require('path');

module.exports = function (context) {
    const projectRoot = context.opts.projectRoot;
    const jsonFilePath = path.join(projectRoot, 'provisioning_info.json');

    const args = process.argv;
    let appGroup;
    let teamID;
    let firstTargetId;
    let firstTargetPPUUID;
    let firstTargetPPName;
    let secondTargetId;
    let secondTargetPPUUID;
    let secondTargetPPName;
    let thirdTargetId;
    let thirdTargetPPUUID;
    let thirdTargetPPName;

    // Iterate over the command-line arguments
    for (const arg of args) {
        if (arg.includes('IOS_EXTENSION_APP_GROUP')) {
            const stringArray = arg.split("=");
            appGroup = stringArray.slice(-1).pop();
        }
        if (arg.includes('IOS_TEAM_ID')) {
            const stringArray = arg.split("=");
            teamID = stringArray.slice(-1).pop();
        }
        if (arg.includes('FIRST_TARGET_ID')) {
            const stringArray = arg.split("=");
            firstTargetId = stringArray.slice(-1).pop();
        }
        if (arg.includes('FIRST_TARGET_PP_UUID')) {
            const stringArray = arg.split("=");
            firstTargetPPUUID = stringArray.slice(-1).pop();
        }
        if (arg.includes('FIRST_TARGET_PP_NAME')) {
            const stringArray = arg.split("=");
            firstTargetPPName = stringArray.slice(-1).pop();
        }
        if (arg.includes('SECOND_TARGET_ID')) {
            const stringArray = arg.split("=");
            secondTargetId = stringArray.slice(-1).pop();
        }
        if (arg.includes('SECOND_TARGET_PP_UUID')) {
            const stringArray = arg.split("=");
            secondTargetPPUUID = stringArray.slice(-1).pop();
        }
        if (arg.includes('SECOND_TARGET_PP_NAME')) {
            const stringArray = arg.split("=");
            secondTargetPPName = stringArray.slice(-1).pop();
        }
        if (arg.includes('THIRD_TARGET_ID')) {
            const stringArray = arg.split("=");
            thirdTargetId = stringArray.slice(-1).pop();
        }
        if (arg.includes('THIRD_TARGET_PP_UUID')) {
            const stringArray = arg.split("=");
            thirdTargetPPUUID = stringArray.slice(-1).pop();
        }
        if (arg.includes('THIRD_TARGET_PP_NAME')) {
            const stringArray = arg.split("=");
            thirdTargetPPName = stringArray.slice(-1).pop();
        }
    }

    // Check if all required arguments are provided
    if (!(appGroup && teamID && firstTargetId && firstTargetPPUUID && firstTargetPPName && secondTargetId && secondTargetPPUUID && secondTargetPPName && thirdTargetId && thirdTargetPPUUID && thirdTargetPPName)) {
        throw new Error('Some required provisioning profile arguments are missing from the command line arguments.');
    }

    // Construct the provisioning info object
    const provisioningInfo = {
        appGroup: appGroup,
        teamID: teamID,
        firstTarget: {
            id: firstTargetId,
            ppUUID: firstTargetPPUUID,
            ppName: firstTargetPPName
        },
        secondTarget: {
            id: secondTargetId,
            ppUUID: secondTargetPPUUID,
            ppName: secondTargetPPName
        },
        thirdTarget: {
            id: thirdTargetId,
            ppUUID: thirdTargetPPUUID,
            ppName: thirdTargetPPName
        }
    };

    // Save the JSON file
    fs.writeFileSync(jsonFilePath, JSON.stringify(provisioningInfo, null, 2), 'utf8');
    console.log(`✅ Provisioning information saved to ${jsonFilePath}`);
};