# Inactive preview metadata endpoint

The shipped web app uses direct browser requests only. This experimental endpoint is not configured in Firebase or deployed by CI. Keeping the Spark free plan is intentional; do not enable billing or deploy this function as part of a web release.

The implementation and its tests are retained for future evaluation. It accepts catalog hosts only, follows at most four redirects to catalog/observed provider hosts, reads at most 128 KiB of HTML, and destroys the upstream response after the head. Video responses are decided from headers without reading their body. It does not download, cache or proxy videos. Run `npm test` from this directory to verify it independently.

The player fetches video directly from its source after Play. It releases the source on pause, hiding and closure. Browser/native media engines choose their streaming buffers; the app never waits for a complete file or creates a media blob. Full-file sharing remains a separate explicit download action.
