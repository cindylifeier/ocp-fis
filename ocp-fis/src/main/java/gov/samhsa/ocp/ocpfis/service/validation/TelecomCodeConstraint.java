package gov.samhsa.ocp.ocpfis.service.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = TelecomCodeValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelecomCodeConstraint {

    String message() default "Invalid Telecom code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
