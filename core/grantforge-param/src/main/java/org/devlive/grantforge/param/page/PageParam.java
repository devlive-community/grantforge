package org.devlive.grantforge.param.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.devlive.grantforge.param.sort.SortModeParam;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PageParam
{

    @NotNull(message = "The number of data displayed per page cannot be empty")
    private Integer size; // page size

    @NotNull(message = "查询页码不能为空")
    @Min(value = 1)
    @Max(value = Integer.MAX_VALUE)
    private Integer page; // current page number

    private Boolean flag = true;

    private String sortColumn; // sort column
    private SortModeParam sortMode; // sort mode

    public Integer getSize()
    {
        return size == 0 ? Integer.MAX_VALUE : size;
    }
}
