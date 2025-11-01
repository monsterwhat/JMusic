package Models;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
public class Settings extends PanacheEntity {

    private String libraryPath;
    
    private Boolean runAsService = false;
    
    private Boolean torrentBrowsingEnabled = false;
    private Boolean torrentPeerDiscoveryEnabled = false;
    private Boolean torrentDiscoveryEnabled = false;
     
    @OneToMany(cascade = CascadeType.ALL, fetch=FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "settings_id")
    private List<SettingsLog> logs = new ArrayList<>();
    
    
}
