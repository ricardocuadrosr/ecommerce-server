package com.zosh.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.zosh.exception.OrderException;
import com.zosh.exception.PaymentException;
import com.zosh.exception.StripeException;
import com.zosh.model.Order;
import com.zosh.repository.OrderRepository;
import com.zosh.response.ApiResponse;
import com.zosh.response.PaymentLinkResponse;
import com.zosh.service.OrderService;
import com.zosh.service.UserService;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @Value("${stripe.api.secret}")
    String apiSecret;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderRepository orderRepository;

    @PostMapping("/payments/{orderId}")
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(@PathVariable Long orderId,
            @RequestHeader("Authorization") String jwt) throws OrderException, PaymentException {

        Order order = orderService.findOrderById(orderId);

        try {
            Stripe.apiKey = apiSecret;
            SessionCreateParams params =
                    SessionCreateParams.builder()
                            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl("http://localhost:3000/payment/"+orderId+"?session_id={CHECKOUT_SESSION_ID}" )
                            .setCancelUrl("http://localhost:3000/payment/cancel")
                            .addLineItem(SessionCreateParams.LineItem.builder()
                                            .setQuantity(1L)
                                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                                            .setCurrency("usd")
                                                            .setUnitAmount((long) (order.getTotalPrice() * 100)) // amount in cents
                                                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                            .setName("Order #" + orderId)
                                                                            .build())
                                                            .build())
                                            .build())
                            .build();

            Session session = Session.create(params);

            PaymentLinkResponse response = new PaymentLinkResponse();
            response.setPayment_link_url(session.getUrl());

            return new ResponseEntity<PaymentLinkResponse>(response, HttpStatus.CREATED);

        } catch (Exception e) {
            throw new PaymentException(e.getMessage());
        }
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse> redirect(@RequestParam(name="payment_id") String paymentId, @RequestParam(name="order_id") Long orderId) throws OrderException, StripeException {
        Order order = orderService.findOrderById(orderId);
        System.out.println("payment id"+paymentId+"-orderid"+orderId);
        try {
            Stripe.apiKey = apiSecret;
            Session session = Session.retrieve(paymentId);
            PaymentIntent paymentIntent = PaymentIntent.retrieve(session.getPaymentIntent());

            if ("succeeded".equals(paymentIntent.getStatus())) {
                order.getPaymetDetails().setPaymentId(paymentId);
                order.getPaymetDetails().setPaymentStatus("COMPLETED");
                order.setOrderStatus("PLACED");
                orderRepository.save(order);

                ApiResponse res = new ApiResponse();
                res.setMessage("Your order has been placed");
                res.setStatus(true);
                return new ResponseEntity<ApiResponse>(res, HttpStatus.ACCEPTED);
            } else {
                throw new PaymentException("Payment not succeeded");
            }
        } catch (Exception e) {
            throw new StripeException(e.getMessage());
        }
    }
}


