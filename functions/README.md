# Preview metadata endpoint

The web player uses this same-origin endpoint when a provider blocks browser metadata reads. It accepts catalog hosts only, follows at most four redirects to catalog/observed provider hosts, reads at most 128 KiB of HTML, and destroys the upstream response after the head. Video responses are decided from headers without reading their body. It does not download, cache or proxy videos.

Run `npm test` from this directory. `npm run build` copies the current shared catalog for packaging; Firebase runs it before deployment. Deploy the function and Hosting together from the project root with `firebase deploy --only functions:preview:previewMetadata,hosting`. The live web workflow deploys the function before Hosting. Deployment requires a Firebase project with Cloud Functions enabled and credentials allowed to deploy functions; the existing Hosting-only account may need additional permissions. PR Hosting previews use the deployed metadata function.

The player fetches video directly from its source after Play. It releases the source on pause, hiding and closure. Browser/native media engines choose their streaming buffers; the app never waits for a complete file or creates a media blob. Full-file sharing remains a separate explicit download action.
