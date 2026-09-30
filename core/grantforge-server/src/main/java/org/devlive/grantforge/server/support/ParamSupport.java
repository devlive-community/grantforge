package org.devlive.grantforge.server.support;

import org.devlive.grantforge.common.page.PageModel;
import org.devlive.grantforge.param.page.PageParam;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.ObjectUtils;

public class ParamSupport
{
    private static final String DEFAULT_ORDER_COLUMN = "createTime";

    /**
     * get pageable from page param
     *
     * @param param page param
     * @return pageable info
     */
    public static Pageable getPageable(PageParam param)
    {
        // sort by column and mode
        if (!ObjectUtils.isEmpty(param.getSortColumn()) && !ObjectUtils.isEmpty(param.getSortMode())) {
            return PageModel.getPageableAndSort(param.getPage(), param.getSize(), Sort.by(Sort.Direction.fromString(param.getSortMode().name()),
                    param.getSortColumn()));
        }
        // sort column is empty, sort by mode
        if (!ObjectUtils.isEmpty(param.getSortMode())) {
            return PageModel.getPageableAndSort(param.getPage(), param.getSize(), Sort.by(Sort.Direction.fromString(param.getSortMode().name()),
                    DEFAULT_ORDER_COLUMN));
        }
        // sort mode is empty, sort by custom column
        if (!ObjectUtils.isEmpty(param.getSortColumn())) {
            return PageModel.getPageableAndSort(param.getPage(), param.getSize(), Sort.by(Sort.Direction.DESC,
                    param.getSortColumn()));
        }
        // 按默认列 createTime 做数据排序
        return PageModel.getPageableAndSort(param.getPage(), param.getSize(), Sort.by(Sort.Direction.ASC,
                DEFAULT_ORDER_COLUMN));
    }
}
