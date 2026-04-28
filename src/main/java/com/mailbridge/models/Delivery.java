package com.mailbridge.models;

/**
 * Representa um envio — uma execução de uma campanha para uma lista de destinatários.
 *
 * O status evolui assim:
 *   pending → running → done
 *                    ↘ error (se todos falharam)
 *
 * sent + failed = total_contacts quando o envio termina.
 */
public class Delivery {
    private int    id;
    private int    userId;
    private int    campaignId;
    private String campaignName;   // JOIN com campaigns — não guardado directamente
    private int    totalContacts;
    private int    sent;
    private int    failed;
    private String status;
    private String createdAt;

    public Delivery() {}
    public Delivery(int id, int userId, int campaignId, String campaignName,
                    int totalContacts, int sent, int failed, String status, String createdAt) {
        this.id = id; this.userId = userId; this.campaignId = campaignId;
        this.campaignName = campaignName; this.totalContacts = totalContacts;
        this.sent = sent; this.failed = failed; this.status = status; this.createdAt = createdAt;
    }

    public int    getId()            { return id; }
    public int    getUserId()        { return userId; }
    public int    getCampaignId()    { return campaignId; }
    public String getCampaignName()  { return campaignName; }
    public int    getTotalContacts() { return totalContacts; }
    public int    getSent()          { return sent; }
    public int    getFailed()        { return failed; }
    public String getStatus()        { return status; }
    public String getCreatedAt()     { return createdAt; }
    public void setId(int id)                        { this.id = id; }
    public void setUserId(int u)                     { this.userId = u; }
    public void setCampaignId(int c)                 { this.campaignId = c; }
    public void setCampaignName(String n)            { this.campaignName = n; }
    public void setTotalContacts(int t)              { this.totalContacts = t; }
    public void setSent(int s)                       { this.sent = s; }
    public void setFailed(int f)                     { this.failed = f; }
    public void setStatus(String s)                  { this.status = s; }
    public void setCreatedAt(String c)               { this.createdAt = c; }
}
