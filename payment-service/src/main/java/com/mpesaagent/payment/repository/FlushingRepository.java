package com.mpesaagent.payment.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * CRUD plus "save and send it to the database now", so a duplicate key is reported on this line and not later.
 * The method has the same shape as the one in Spring Data JPA's own JpaRepository, so SQL needs nothing extra.
 */
@NoRepositoryBean
public interface FlushingRepository<T, ID> extends CrudRepository<T, ID> {

    <S extends T> S saveAndFlush(S entity);
}
