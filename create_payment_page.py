import httpx, json

base = "http://localhost:8081/tools"
h = {"Content-Type": "application/json"}

# Step 1: Clear cart and add 1 iPhone fresh
httpx.post(f"{base}/add_to_cart/execute", headers=h,
           json={"context": {"userId": 1}, "arguments": {"productId": 5, "quantity": 1}})

# Step 2: Create a new PayPal order
r = httpx.post(f"{base}/create_order/execute", headers=h,
               json={"context": {"userId": 1}, "arguments": {}}, timeout=30)
checkout = r.json()

if not checkout.get("success"):
    print("Checkout failed:", checkout)
    exit(1)

result = checkout["result"]
order_id = result.get("paypalOrderId") or result.get("razorpayOrderId")
client_id = result.get("clientId") or result.get("keyId")
amount = result["amount"]
currency = result.get("currency", "USD")
approval_url = result.get("approvalUrl", "")
local_order_id = result["orderId"]

print(f"PayPal Order: {order_id}")
print(f"Amount: {currency} {amount/100:.2f}")

# Step 3: Write the real payment HTML page
html = f"""<!DOCTYPE html>
<html>
<head>
  <title>Pay for iPhone - Order #{local_order_id}</title>
  <style>
    body {{ font-family: Arial, sans-serif; max-width: 500px; margin: 80px auto; text-align: center; }}
    h2 {{ color: #333; }}
    .amount {{ font-size: 2em; color: #0070ba; font-weight: bold; margin: 10px 0; }}
    #paypal-button-container {{ margin-top: 24px; min-height: 120px; }}
    #status {{ margin-top: 30px; padding: 20px; border-radius: 8px; display: none; }}
    .success {{ background: #e6f4ea; color: #137333; border: 1px solid #34a853; }}
    .error {{ background: #fce8e6; color: #c5221f; border: 1px solid #ea4335; }}
    pre {{ text-align: left; font-size: 12px; background: #f5f5f5; padding: 10px; border-radius: 4px; overflow-x: auto; }}
  </style>
  <script src="https://www.paypal.com/sdk/js?client-id={client_id}&currency={currency}&intent=capture"></script>
</head>
<body>
  <h2>Complete Your Purchase with PayPal</h2>
  <p>iPhone x 1</p>
  <div class="amount">${amount/100:.2f}</div>
  <p>Order Reference: <code>{order_id}</code></p>

  <div id="paypal-button-container"></div>

  {f'<p><a href="{approval_url}" target="_blank" style="color: #0070ba; font-size: 13px;">Or click here to open PayPal hosted page</a></p>' if approval_url else ''}

  <div id="status"></div>

  <script>
    if (window.paypal) {{
      paypal.Buttons({{
        style: {{
          layout: 'vertical',
          color: 'gold',
          shape: 'rect',
          label: 'paypal'
        }},
        createOrder: function() {{
          return "{order_id}";
        }},
        onApprove: function(data) {{
          showStatus("Capturing and verifying payment...", "");
          fetch("http://localhost:8081/tools/verify_payment/execute", {{
            method: "POST",
            headers: {{"Content-Type": "application/json"}},
            body: JSON.stringify({{
              context: {{userId: 1}},
              arguments: {{
                paypalOrderId: data.orderID || "{order_id}",
                paypalPaymentId: data.paymentID || ""
              }}
            }})
          }})
          .then(r => r.json())
          .then(data => {{
            if (data.success && data.result.verified) {{
              showStatus(
                "Payment Successful! Order #" + data.result.order.id + " is PAID.<br>" +
                "<pre>" + JSON.stringify(data.result.order, null, 2) + "</pre>",
                "success"
              );
            }} else {{
              showStatus("Verification failed: " + JSON.stringify(data), "error");
            }}
          }})
          .catch(e => showStatus("Error: " + e, "error"));
        }},
        onError: function(err) {{
          showStatus("PayPal Error: " + err, "error");
        }}
      }}).render('#paypal-button-container');
    }} else {{
      showStatus("Could not load PayPal SDK. Please check your internet connection.", "error");
    }}

    function showStatus(msg, type) {{
      var el = document.getElementById("status");
      el.innerHTML = msg;
      el.className = type;
      el.style.display = "block";
    }}
  </script>
</body>
</html>"""

with open("payment_page.html", "w", encoding="utf-8") as f:
    f.write(html)

print(f"\nPayment page written: payment_page.html")
print(f"Open this file in your browser to pay with PayPal!")
