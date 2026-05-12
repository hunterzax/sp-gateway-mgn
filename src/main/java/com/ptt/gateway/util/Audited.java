package com.ptt.gateway.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** Machine-readable action key, e.g. "updateShipper", "deleteTag" */
    String action();

    /**
     * Human-readable template shown on frontend. Supports {user} and {resource}.
     */
    String descriptionTemplate();

    /** Log type: ACTION, USER_MANAGEMENT, ACCESS_HISTORY, NGINX_ACCESS */
    String logType() default "ACTION";

    /** Severity: INFO, WARNING, ERROR */
    String severity() default "INFO";

    /** Source system: BACKEND, AUTH_SERVICE, NGINX, etc. */
    String source() default "BACKEND";

    /** Status type shown in frontend, e.g. "Action", "Delete" */
    String statusType() default "Action";

    /**
     * Name of the method parameter whose value will replace {resource} in
     * descriptionTemplate.
     * Leave empty if {resource} is not used.
     */
    String resourceParam() default "";

    /**
     * Name of a field on the method's return value whose string representation
     * will replace {@code {id}} in descriptionTemplate.
     * Useful when the entity ID is auto-generated inside the method.
     * Leave empty if {@code {id}} is not used.
     */
    String resultIdField() default "";

    /**
     * Names of fields on the method's return value to extract and replace
     * as {@code {fieldName}} placeholders in descriptionTemplate.
     * Example: resultFields = {"accountId", "email"} with template
     * "{user} adds new user : {accountId} ({email})"
     */
    String[] resultFields() default {};
}
