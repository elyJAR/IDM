chrome.downloads.onCreated.addListener((downloadItem) => {
    // 1. Cancel default browser download
    chrome.downloads.cancel(downloadItem.id);
    
    const url = downloadItem.url;
    const filename = downloadItem.filename ? downloadItem.filename.split(/[\/\\]/).pop() : "";

    console.log("FastDL Intercepted download:", url);

    // 2. Fetch cookies for the domain
    chrome.cookies.getAll({url: url}, (cookies) => {
        let cookieString = (cookies || []).map(c => `${c.name}=${c.value}`).join('; ');
        sendToAndroidApp(url, cookieString, filename);
    });
});

function sendToAndroidApp(url, cookies, filename) {
    const encodedUrl = encodeURIComponent(url);
    const encodedCookies = encodeURIComponent(cookies || "");
    const encodedFilename = encodeURIComponent(filename || "");
    
    // Official Android Intent URI format for Chromium browsers (Kiwi / Lemur / Samsung Internet)
    const intentUrl = `intent://download?url=${encodedUrl}&cookie=${encodedCookies}&filename=${encodedFilename}#Intent;scheme=fastdl;package=com.fastdl.app;action=android.intent.action.VIEW;end;`;
    
    chrome.tabs.create({ url: intentUrl, active: true }, (tab) => {
        setTimeout(() => {
            if (tab && tab.id) {
                chrome.tabs.remove(tab.id);
            }
        }, 1500);
    });
}
