// package com.qingsong.ai.config;
//
// import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
// import org.apache.ibatis.reflection.MetaObject;
// import org.springframework.stereotype.Component;
//
// import java.time.LocalDateTime;
//
// /**
//  * MyBatis Plus 自动填充处理器
//  * 用于自动填充创建时间和更新时间
//  *
//  * @author caojiangjiang
//  * @version 1.0
//  * @since 2026-04-28
//  */
// @Component
// public class MyMetaObjectHandler implements MetaObjectHandler {
//
//     /**
//      * 插入时的自动填充
//      */
//     @Override
//     public void insertFill(MetaObject metaObject) {
//         // 如果创建时间为空，则自动填充
//         if (metaObject.getValue("creatDate") == null) {
//             this.strictInsertFill(metaObject, "creatDate", LocalDateTime.class, LocalDateTime.now());
//         }
//
//         // 如果更新时间为空，则自动填充
//         if (metaObject.getValue("updatDate") == null) {
//             this.strictInsertFill(metaObject, "updatDate", LocalDateTime.class, LocalDateTime.now());
//         }
//     }
//
//     /**
//      * 更新时的自动填充
//      */
//     @Override
//     public void updateFill(MetaObject metaObject) {
//         // 更新时自动填充更新时间
//         this.strictUpdateFill(metaObject, "updatDate", LocalDateTime.class, LocalDateTime.now());
//     }
// }
