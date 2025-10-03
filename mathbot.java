import openai
from flask import Flask, request, jsonify
import sympy as sp

app = Flask(__name__)

# Replace with your OpenAI API key
OPENAI_API_KEY = "YOUR_OPENAI_API_KEY"

def solve_math_query(query):
    try:
        expr = sp.sympify(query)
        result = expr.evalf()
        return f"The answer is: {result}"
    except Exception:
        return None

def ask_gpt_math(query, chat_history):
    response = openai.ChatCompletion.create(
        model="gpt-3.5-turbo",
        api_key=OPENAI_API_KEY,
        messages=chat_history + [{"role": "user", "content": query}],
        temperature=0.2,
    )
    return response['choices'][0]['message']['content']

@app.route("/chat", methods=["POST"])
def chat():
    data = request.json
    query = data.get("query", "")
    chat_history = data.get("history", [])  # List of {"role": "user"/"assistant", "content": ...}

    # Try to solve as math
    math_result = solve_math_query(query)
    if math_result:
        answer = math_result
    else:
        # Use LLM for general math explanation/chat
        answer = ask_gpt_math(query, chat_history)

    return jsonify({"answer": answer})

if __name__ == "__main__":
    app.run(debug=True)
