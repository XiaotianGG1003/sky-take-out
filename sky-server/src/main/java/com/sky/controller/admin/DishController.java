package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "菜品相关接口")
@RequestMapping("/admin/dish")
@Slf4j
public class DishController {

    @Autowired
    DishService dishService;

    @Autowired
    RedisTemplate redisTemplate;

    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品 {}", dishDTO);
        dishService.saveWithFlavors(dishDTO);

        String key = "dish_" + dishDTO.getCategoryId();
        redisTemplate.delete(key);

        return Result.success();
    }

    /**
     * 分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }


    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除菜品{}", ids);
        dishService.deleteBatch(ids);

        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        return Result.success();
    }

    /**
     * 根据分类id查询菜品
     * @param categoryId
     * @return
     */
    @ApiOperation("根据分类id查询菜品")
    @GetMapping("/list")
    public Result<List<Dish>> getByCategoryId(@RequestParam Long categoryId) {
        log.info("查询分类菜品 categoryId:{}", categoryId);
        List<Dish> list = dishService.getByCategoryId(categoryId);
        return Result.success(list);
    }


    /**
     * 根据id查询菜品
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("查询菜品id {}", id);
        DishVO dishVO = dishService.getWithFlavor(id);
        return Result.success(dishVO);
    }

    /**
     * 修改菜品信息
     * @param dishDTO
     * @return
     */
    @PutMapping
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品信息{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        return Result.success();
    }

    /**
     * 菜品起售、停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    public Result setStatus(@PathVariable Integer status, @RequestParam Long id) {
        log.info("菜品id:{} 状态:{}", status, id);
        dishService.setStatus(status, id);

        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        return Result.success();
    }

}
