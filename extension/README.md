# Email Writer — Chrome Extension

Injects an **AI Reply** button and a tone selector into the Gmail compose toolbar.
On click it sends the open email's content to the backend
(`POST http://localhost:8081/api/email/generate`) and inserts the generated reply
into the compose box.

## Load it (unpacked)

1. Start the backend (`../email-writer-sb`, port 8081).
2. Open `chrome://extensions`, enable **Developer mode** (top right).
3. Click **Load unpacked** and select this `extension/` folder.
4. Open Gmail, click **Reply** on a message — an **AI Reply** button and a tone
   dropdown appear in the compose toolbar.

## Files

- `manifest.json` — MV3 manifest (content script on `mail.google.com`).
- `content.js` — MutationObserver compose detection, button + tone selector
  injection, backend call, reply insertion.
- `content.css` — button + tone selector styling.
