package org.devlive.grantforge.common.page;

import org.springframework.data.domain.*;

import java.util.List;

public class PageModel<T> extends PageImpl<T> implements Page<T> {

    /**
     * Constructor of {@code PageImpl}.
     *
     * @param content  the content of this page, must not be {@literal null}.
     * @param pageable the paging information, can be {@literal null}.
     * @param total    the total amount of items available. The total might be adapted considering the length of the content
     */
    public PageModel(List<T> content, Pageable pageable, long total) {
        super(content, pageable, total);
    }

    /**
     * Front-end Passenger 1 is the start page and 0 of spring data JPA is the start page.
     */
    public static Pageable request(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber() - 1, pageable.getPageSize(), pageable.getSort());
    }

    public static Pageable getPageable(Integer page, Integer size) {
        return PageModel.request(PageRequest.of(page, size));
    }

    /**
     * get pageable info from page info and sort
     *
     * @param page page number
     * @param size page size
     * @param sort data sort
     * @return pageable info
     */
    public static Pageable getPageableAndSort(Integer page, Integer size, Sort sort) {
        return PageModel.request(PageRequest.of(page, size, sort));
    }

    /**
     * Rewrite the current page, add 1 to the current page and return to the front desk after adding 0 to 1 to the start page of spring data JPA
     */
    @Override
    public int getNumber() {
        return super.getNumber() + 1;
    }
}
