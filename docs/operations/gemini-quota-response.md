# Gemini quota incident response

1. Open Admin **Platform health → AI provider** and record circuit reason, model, mode, project label, application-tracked dimensions and alert time. Do not copy credentials or provider payloads.
2. Confirm authoritative project tier and quota in Google AI Studio. Internal percentages are safety estimates only.
3. For minute rate limiting, wait for the displayed estimated recheck. It is not a guaranteed provider reset.
4. For daily or unknown 429, leave the free-quota pause in place until the conservative daily boundary. Do not repeatedly recheck.
5. For invalid credentials/model, correct deployment secrets/configuration and use **Recheck safely**. Never put the key in the browser.
6. For billing-safety or manual disable, investigate and redeploy explicit configuration. These states do not auto-resume and FREE_ONLY has no “resume anyway”.
7. Acknowledge the persistent alert after recording the operational response. Normal content work continues while AI is paused.

If containment is needed, set `AI_USAGE_MODE=DISABLED` or use the audited Admin disable action. Never switch normal runtime to FAKE as a hidden fallback.

