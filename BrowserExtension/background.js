chrome.downloads.onCreated.addListener((downloadItem) => {
    // 1. Cancel the default browser download
    chrome.downloads.cancel(downloadItem.id);
    
    const url = downloadItem.url;
    const filename = downloadItem.filename;

    console.log("Intercepted download:", url);

    // 2. Fetch cookies for the domain to handle authenticated downloads
    chrome.cookies.getAll({url: url}, (cookies) => {
        let cookieString = cookies.map(c => `${c.name}=${c.value}`).join('; ');
        
        // 3. Hand-off to Android App via Intent URL scheme
        sendToAndroidApp(url, cookieString, filename);
    });
});

function sendToAndroidApp(url, cookies, filename) {
    const encodedUrl = encodeURIComponent(url);
    const encodedCookies = encodeURIComponent(cookies || "");
    const encodedFilename = encodeURIComponent(filename || "");
    
    // The fastdl scheme will be intercepted by our native Android app
    const intentUrl = `fastdl://download?url=${encodedUrl}&cookie=${encodedCookies}&filename=${encodedFilename}`;
    
    // This creates a new tab that navigates to the intent, which Android intercepts and opens our app
    chrome.tabs.create({ url: intentUrl, active: false }, (tab) => {
        // Automatically close the tab after a short delay
        setTimeout(() => {
            if(tab && tab.id) {
                chrome.tabs.remove(tab.id);
            }
        }, 1000);
    });
}
