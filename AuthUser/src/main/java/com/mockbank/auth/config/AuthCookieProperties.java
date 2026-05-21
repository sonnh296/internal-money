package com.mockbank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.cookies")
public class AuthCookieProperties {

  /** Bật HttpOnly cookie thay vì chỉ trả token trong JSON (frontend không lưu JWT). */
  private boolean enabled = false;
  private boolean secure = false;
  private String sameSite = "Lax";
  private String path = "/";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isSecure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public String getSameSite() {
    return sameSite;
  }

  public void setSameSite(String sameSite) {
    this.sameSite = sameSite;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}
