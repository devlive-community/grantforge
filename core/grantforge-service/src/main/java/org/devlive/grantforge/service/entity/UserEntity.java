package org.devlive.grantforge.service.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(value = AuditingEntityListener.class)
@Table(name = "grantforge_user")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(value = {
        "password",
})
public class UserEntity extends BaseEntity
{
    @Column(name = "password")
    private String password; // 密码

    @Column(name = "avatar")
    private String avatar; // 头像

    @Column(name = "email")
    private String email; // 邮箱

    @Column(name = "locked")
    private Boolean locked = false; // 是否锁定

    @Column(name = "is_system")
    private Boolean isSystem = false; // 是否为系统默认,系统默认用户无法做任何操作

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "grantforge_user_role_relation",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    private List<RoleEntity> roles;
}
