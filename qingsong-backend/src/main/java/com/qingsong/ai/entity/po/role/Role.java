package com.qingsong.ai.entity.po.role;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Objects;

/**
 * description...
 *
 * @author : caojiangjiang
 * @data : 2025/05/05 02:27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    private String id;
    private String name;
    private String value;
    private String valueEn;
    private String favor;
    private Double temperature;
    private Long sort;
    @TableField(fill = FieldFill.INSERT)
    private Date createDate;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateDate;
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id) && Objects.equals(name, role.name) && Objects.equals(value, role.value) && Objects.equals(valueEn, role.valueEn) && Objects.equals(createDate, role.createDate) && Objects.equals(updateDate, role.updateDate) && Objects.equals(description, role.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, value, valueEn, createDate, updateDate, description);
    }
}
