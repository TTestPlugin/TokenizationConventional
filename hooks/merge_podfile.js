const fs = require('fs');
const path = require('path');

module.exports = function (context) {
    const projectRoot = context.opts.projectRoot;
    const podfilePath = path.join(projectRoot, 'platforms', 'ios', 'Podfile');
    const customPodfilePath = path.join(context.opts.plugin.dir, 'src', 'ios', 'Podfile');

    if (!fs.existsSync(customPodfilePath)) {
        console.error("Custom Podfile fragment not found!");
        return;
    }

    if (fs.existsSync(podfilePath)) {
        let podfileContent = fs.readFileSync(podfilePath, 'utf-8');
        const customPodfileContent = fs.readFileSync(customPodfilePath, 'utf-8');

        if (!podfileContent.includes(customPodfileContent)) {
            podfileContent += `\n# Added by Cordova Plugin\n${customPodfileContent}`;
            fs.writeFileSync(podfilePath, podfileContent, 'utf-8');
            console.log("Custom Podfile content merged successfully.");
        } else {
            console.log("Custom Podfile content already present.");
        }
    } else {
        console.error("Main Podfile not found. Ensure iOS platform is added.");
    }
};
