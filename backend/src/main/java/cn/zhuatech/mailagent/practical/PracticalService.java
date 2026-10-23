/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.mailagent.practical;

import static cn.zhuatech.mailagent.practical.PracticalInput.*;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@Service
public class PracticalService {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Map<String, Object> execute(Map<String, Object> r) {
    validate(r);
    keys(r, "subject", "allowedDomains", "maxAttachmentBytes", "recipients", "attachments");
    String subject = text(r, "subject", 200);
    if (subject.contains("\r") || subject.contains("\n"))
      throw new IllegalArgumentException("主题不能包含换行");
    Set<String> domains = new HashSet<>();
    for (String d : strings(r, "allowedDomains", 0, 100)) {
      String domain = d.toLowerCase(Locale.ROOT);
      if (!domain.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") || domain.contains(".."))
        throw new IllegalArgumentException("域名格式错误");
      domains.add(domain);
    }
    int max = integer(r, "maxAttachmentBytes", 0, 100000000);
    List<Map<String, Object>> recipients = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int duplicate = 0, external = 0, to = 0;
    for (var row : rows(r, "recipients", 1, 100)) {
      keys(row, "role", "address");
      String role = text(row, "role", 3), address = text(row, "address", 254);
      if (!Set.of("TO", "CC", "BCC").contains(role)) throw new IllegalArgumentException("收件人角色错误");
      if (!address.matches(
          "[A-Za-z0-9.!#$%&'*+/=?^_{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?"))
        throw new IllegalArgumentException("不支持的邮件地址格式");
      int at = address.indexOf('@');
      String local = address.substring(0, at),
          domain = address.substring(at + 1).toLowerCase(Locale.ROOT);
      if (local.startsWith(".")
          || local.endsWith(".")
          || local.contains("..")
          || domain.contains("..")) throw new IllegalArgumentException("邮件地址格式错误");
      String canonical = local + "@" + domain;
      if (!seen.add(canonical)) {
        duplicate++;
        continue;
      }
      boolean outside = !domains.contains(domain);
      if (outside) external++;
      if (role.equals("TO")) to++;
      recipients.add(Map.of("role", role, "address", canonical, "external", outside));
    }
    long bytes = 0;
    for (var a : rows(r, "attachments", 0, 20)) {
      keys(a, "name", "bytes");
      String name = text(a, "name", 180);
      if (name.contains("\r") || name.contains("\n"))
        throw new IllegalArgumentException("附件名称不能换行");
      bytes += integer(a, "bytes", 0, 100000000);
    }
    List<String> issues = new ArrayList<>();
    if (to == 0) issues.add("MISSING_TO");
    if (bytes > max) issues.add("ATTACHMENT_LIMIT");
    if (external > 0) issues.add("EXTERNAL_RECIPIENT_REVIEW");
    return Map.of(
        "recipients",
        recipients,
        "duplicateCount",
        duplicate,
        "externalCount",
        external,
        "attachmentBytes",
        bytes,
        "issues",
        issues,
        "readyForReview",
        issues.isEmpty(),
        "mailSent",
        false);
  }
}
