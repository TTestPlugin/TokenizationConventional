const fs = require('fs');
const path = require('path');

module.exports = function (context) {
    const projectRoot = context.opts.projectRoot;
    const buildJsPath = path.join(projectRoot, 'node_modules', 'cordova-ios', 'lib', 'build.js');
    const jsonFilePath = path.join(projectRoot, 'provisioning_info.json');

    console.log(`📄 Project Root: ${projectRoot}`);
    console.log(`📄 Path to build.js: ${buildJsPath}`);
    console.log(`📄 Path to provisioning_info.json: ${jsonFilePath}`);

    // Check if the provisioning_info.json file exists
    if (!fs.existsSync(jsonFilePath)) {
        console.error('🚨 provisioning_info.json file not found at the expected path.');
        return;
    }

    // Read and parse the JSON file
    let provisioningInfo;
    try {
        const jsonData = fs.readFileSync(jsonFilePath, 'utf8');
        provisioningInfo = JSON.parse(jsonData);
        console.log(`✅ Successfully read and parsed provisioning_info.json.`);
    } catch (error) {
        console.error(`🚨 Error reading or parsing provisioning_info.json: ${error.message}`);
        console.error('Ensure the JSON file has valid syntax and all required fields.');
        return;
    }

    // Extract provisioning information from nested structure
    const firstTarget = provisioningInfo.firstTarget || {};
    const secondTarget = provisioningInfo.secondTarget || {};
    const thirdTarget = provisioningInfo.thirdTarget || {};

    const firstTargetId = firstTarget.id;
    const firstTargetPP = firstTarget.ppUUID;
    const secondTargetId = secondTarget.id;
    const secondTargetPP = secondTarget.ppUUID;
    const thirdTargetId = thirdTarget.id;
    const thirdTargetPP = thirdTarget.ppUUID;

    console.log(`👉 firstTargetId: ${firstTargetId}, firstTargetPP: ${firstTargetPP}`);
    console.log(`👉 secondTargetId: ${secondTargetId}, secondTargetPP: ${secondTargetPP}`);
    console.log(`👉 thirdTargetId: ${thirdTargetId}, thirdTargetPP: ${thirdTargetPP}`);

    // Validate the presence of all required fields
    if (!firstTargetId || !firstTargetPP || !secondTargetId || !secondTargetPP || !thirdTargetId || !thirdTargetPP) {
        console.error('🚨 Required provisioning profile information is missing in provisioning_info.json.');
        console.error('Please ensure the JSON file includes all target IDs and provisioning profiles.');
        return;
    }

    // Read the build.js file
    fs.readFile(buildJsPath, 'utf8', (err, buildJsContent) => {
        if (err) {
            console.error(`🚨 Error reading build.js: ${err.message}`);
            return;
        }

        console.log('📄 Successfully read build.js content.');

        // Define the new provisioningProfiles block for three targets
        const newProvisioningProfileBlock = `
            exportOptions.provisioningProfiles = {
                "${firstTargetId}": "${firstTargetPP}",
                "${secondTargetId}": "${secondTargetPP}",
                "${thirdTargetId}": "${thirdTargetPP}"
            };
            exportOptions.signingStyle = 'manual';`;

        // String to remove (the entire block you mentioned)
        const oldProvisioningBlock = `
            if (buildOpts.provisioningProfile && bundleIdentifier) {
                if (typeof buildOpts.provisioningProfile === 'string') {
                    exportOptions.provisioningProfiles = { [bundleIdentifier]: String(buildOpts.provisioningProfile) };
                } else {
                    events.emit('log', 'Setting multiple provisioning profiles for signing');
                    exportOptions.provisioningProfiles = buildOpts.provisioningProfile;
                }
                exportOptions.signingStyle = 'manual';
            }`;

        // Replace the old provisioning profile block with the new one
        const modifiedBuildJsContent = buildJsContent.replace(oldProvisioningBlock, newProvisioningProfileBlock);

        // Write the updated build.js back to disk
        fs.writeFile(buildJsPath, modifiedBuildJsContent, 'utf8', (err) => {
            if (err) {
                console.error(`🚨 Error writing modified build.js: ${err.message}`);
                return;
            }

            console.log('✅ Successfully updated build.js with new provisioning profiles.');
        });
    });
};