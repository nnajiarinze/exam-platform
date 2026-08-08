<#macro emailLayout>
<!doctype html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<body style="margin:0;background:#f4f7f5;color:#17352d;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f7f5;padding:24px 12px;">
    <tr><td align="center"><table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #dbe6e1;border-radius:16px;overflow:hidden;">
      <tr><td style="background:#0b5d4b;color:#ffffff;padding:22px 28px;font-size:22px;font-weight:700;">Medbo</td></tr>
      <tr><td style="padding:28px;font-size:16px;line-height:1.55;"><#nested></td></tr>
      <tr><td style="padding:18px 28px;background:#eef5f2;color:#587069;font-size:13px;line-height:1.45;"><#if locale.language == "sv">Detta är ett automatiskt säkerhetsmeddelande från Medbo. Svara inte med lösenord eller andra känsliga uppgifter.<#else>This is an automated security message from Medbo. Never reply with a password or other sensitive information.</#if></td></tr>
    </table></td></tr>
  </table>
</body>
</html>
</#macro>
