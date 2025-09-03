const PLUGIN_ID = "com.ahlibank.tokenization";

module.exports = function (context) {
    var child_process = require('child_process'); // consistent naming
    var Q = require('q'); // correct usage of Q
    var deferral = Q.defer(); // create a deferred object

    console.log('Installing "' + PLUGIN_ID + '" dependencies');
    child_process.exec('npm install --production', {cwd: __dirname}, function (error) {
        if (error !== null) {
            console.log('exec error: ' + error);
            deferral.reject('npm installation failed');
        } else {
            deferral.resolve();
        }
    });

    return deferral.promise;
};

// vim: ts=4:sw=4:et
