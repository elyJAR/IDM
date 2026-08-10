document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('btn-settings').addEventListener('click', () => {
        // Attempt to launch the FastDL Android App settings via intent
        const intentUrl = `fastdl://settings`;
        chrome.tabs.create({ url: intentUrl, active: true }, (tab) => {
            setTimeout(() => {
                if(tab && tab.id) {
                    chrome.tabs.remove(tab.id);
                }
            }, 1000);
        });
    });
});
