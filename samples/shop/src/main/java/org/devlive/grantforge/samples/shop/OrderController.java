// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.shop;

import org.devlive.grantforge.sdk.DataAction;
import org.devlive.grantforge.sdk.GrantForge;
import org.devlive.grantforge.sdk.GrantForgeDataScopes;
import org.devlive.grantforge.sdk.RequirePermission;
import org.devlive.grantforge.sdk.UserAuthorization;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The shop's API. Each endpoint needs an API permission of GrantForge (@RequirePermission), and reads or deletes only the
 * orders GrantForge's data policies let the user use: the scope is just another Specification.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController
{
    private final ShopOrderRepository orders;
    private final GrantForge grantForge;
    private final GrantForgeDataScopes scopes;
    private final Clock clock = Clock.systemUTC();

    /**
     * Creates the controller.
     *
     * @param orders the orders
     * @param grantForge who the user is
     * @param scopes which orders the user may use
     */
    public OrderController(ShopOrderRepository orders, GrantForge grantForge, GrantForgeDataScopes scopes)
    {
        this.orders = requireNonNull(orders, "orders");
        this.grantForge = requireNonNull(grantForge, "grantForge");
        this.scopes = requireNonNull(scopes, "scopes");
    }

    /**
     * Lists the orders the user may read, newest first.
     *
     * @return the orders
     */
    @RequirePermission("orders.read")
    @GetMapping
    public List<OrderView> list()
    {
        return orders.findAll(scopes.scope(ShopOrder.class, DataAction.READ), Sort.by(Sort.Direction.DESC, "placedAt")).stream()
                .map(OrderView::from).toList();
    }

    /**
     * Places an order for the user.
     *
     * @param order what to order
     * @return the order
     */
    @RequirePermission("orders.create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@RequestBody NewOrder order)
    {
        UserAuthorization user = grantForge.current();
        return OrderView.from(orders.save(new ShopOrder(Long.parseLong(user.accountId()), user.username(), order.title(), order.total(),
                clock.instant())));
    }

    /**
     * Deletes an order the user may delete; others look absent.
     *
     * @param id the order
     */
    @RequirePermission("orders.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id)
    {
        Specification<ShopOrder> deletable = scopes.scope(ShopOrder.class, DataAction.DELETE);
        Specification<ShopOrder> byId = (root, query, builder) -> builder.equal(root.get("id"), id);
        ShopOrder order = orders.findOne(deletable.and(byId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        orders.delete(order);
    }

    /**
     * A new order.
     *
     * @param title what is ordered
     * @param total the price
     */
    public record NewOrder(String title, long total)
    {
    }

    /**
     * An order as the front end shows it.
     *
     * @param id the order
     * @param owner who placed it
     * @param title what is ordered
     * @param status where it stands
     * @param total the price
     */
    public record OrderView(long id, String owner, String title, ShopOrder.Status status, long total)
    {
        static OrderView from(ShopOrder order)
        {
            return new OrderView(requireNonNull(order.getId(), "id"), order.getOwnerName(), order.getTitle(), order.getStatus(),
                    order.getTotal());
        }
    }
}
