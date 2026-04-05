from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

def make_icici_savings(path):
    c = canvas.Canvas(path, pagesize=A4)
    lines = [
        "ICICI Bank",
        "Account Statement",
        "Date        Narration                           Debit     Credit    Balance",
        "01/01/2024  UPI/Swiggy/Order/9876543210        349.00               45000.00",
        "02/01/2024  UPI/Zomato/Food/1234567890         280.00               44720.00",
        "03/01/2024  NEFT/HDFC/SalaryCredit                       50000.00   94720.00",
        "05/01/2024  ATM WDL/SBI ATM/Mumbai             5000.00              89720.00",
        "07/01/2024  POS/Amazon/Online Shopping         1500.00              88220.00",
        "10/01/2024  UPI/Swiggy/Order/1122334455        349.00               87871.00",
        "12/01/2024  IMPS/PhonePe/Transfer              2000.00              85871.00",
        "15/01/2024  ECS/HDFC/EMI Loan Debit            8000.00              77871.00",
        "18/01/2024  UPI/Uber/Ride/9988776655           250.00               77621.00",
        "20/01/2024  NEFT/ICICI/Rent Payment           15000.00              62621.00",
        "22/01/2024  UPI/Swiggy/Order/5544332211        349.00               62272.00",
        "25/01/2024  POS/BigBazaar/Groceries            3200.00              59072.00",
        "28/01/2024  UPI/IRCTC/Train Ticket             1200.00              57872.00",
        "01/02/2024  UPI/Swiggy/Order/6677889900        349.00               57523.00",
        "05/02/2024  NEFT/HDFC/SalaryCredit                       50000.00  107523.00",
        "08/02/2024  ATM WDL/ICICI ATM/Delhi            3000.00             104523.00",
        "10/02/2024  UPI/Zomato/Food/2233445566         420.00              104103.00",
        "14/02/2024  POS/Myntra/Online Shopping         2800.00             101303.00",
        "18/02/2024  NACH/LIC/Insurance Premium         5000.00              96303.00",
        "20/02/2024  UPI/Uber/Ride/1234567891           180.00               96123.00",
    ]
    y = 780
    for line in lines:
        c.drawString(40, y, line)
        y -= 18
    c.save()
    print(f"Created: {path}")

make_icici_savings("C:/CC/bank-statement-analyzer/test_statement.pdf")
