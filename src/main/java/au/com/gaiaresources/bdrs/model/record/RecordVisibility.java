package au.com.gaiaresources.bdrs.model.record;

public enum RecordVisibility {
    // only the owner and admin can ever see this record
    OWNER_ONLY("Owner only"),         
    // owner and admin have full access, other users can see a simplified 
    // version of the record and can request more information from the owner
    // via BDRS notification
    CONTROLLED("Limited public viewing"),
    // everyone can see all details
    PUBLIC("Full public access");
    
    private String desc;
    
    private RecordVisibility(String desc) {
        this.desc = desc;
    }
    
    public String getDescription() {
        return this.desc;
    }
    
    public static RecordVisibility parse(String str) {
        return RecordVisibility.valueOf(str);
    }
    
    public static RecordVisibility parse(String str, RecordVisibility defaultValue) {
        for (RecordVisibility rv : RecordVisibility.values()) {
            if (rv.toString().equals(str)) {
                return rv;
            }
        }
        return defaultValue;
    }
}
