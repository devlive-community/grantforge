package org.devlive.grantforge.service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.devlive.grantforge.service.entity.icon.IconModel;
import org.devlive.grantforge.service.entity.system.menu.SystemMenuTypeModel;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(value = AuditingEntityListener.class)
@Table(name = "grantforge_menu")
public class MenuEntity extends BaseEntity
{
    @Column(name = "code")
    private String code;

    @Column(name = "url")
    private String url;

    @Column(name = "sorted")
    private Integer sorted;

    @Column(name = "level")
    private Integer level;

    @Column(name = "tips")
    private String tips;

    @Column(name = "newd")
    private Boolean newd;

    @Column(name = "parent")
    private Long parent;

    @Column(name = "description")
    private String description;

    @Column(name = "is_system")
    private Boolean isSystem;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "system_menu_type_relation",
            joinColumns = @JoinColumn(name = "system_menu_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "system_menu_type_id", referencedColumnName = "id"))
    private SystemMenuTypeModel type;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "grantforge_menu_method_relation",
            joinColumns = @JoinColumn(name = "menu_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "method_id", referencedColumnName = "id"))
    private List<MethodEntity> methods;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinTable(name = "system_menu_icon_relation",
            joinColumns = @JoinColumn(name = "system_menu_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "icon_id", referencedColumnName = "id"))
    private IconModel icon;

    public MenuEntity(Long id)
    {
        this.setId(id);
    }
}
