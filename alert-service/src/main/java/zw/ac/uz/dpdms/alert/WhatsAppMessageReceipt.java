package zw.ac.uz.dpdms.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "whatsapp_message_receipt")
public class WhatsAppMessageReceipt {
  @Id
  @Column(name = "provider_message_id", length = 200)
  public String providerMessageId;

  @Column(name = "alert_id", nullable = false)
  public Long alertId;

  protected WhatsAppMessageReceipt() {}

  public WhatsAppMessageReceipt(String providerMessageId, Long alertId) {
    this.providerMessageId = providerMessageId;
    this.alertId = alertId;
  }
}
