package com.dehui.property.modules.user.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_user")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    private String realName;
    
    private String phone;
    
    private String email;
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    private String role;
}
