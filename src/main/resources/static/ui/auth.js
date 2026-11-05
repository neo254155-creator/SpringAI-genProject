import { configureApi } from "./api-config.js";
import { accountAction, safeNext } from "./auth-client.js";
let register = false, busy = false;
const field = id => document.getElementById(id);
let configuration;
function prepare() {
  return configuration ||= configureApi().catch(error => { configuration = null; throw error; });
}
prepare().catch(() => {});
field("toggle-auth").addEventListener("click", () => {
  register = !register; field("auth-error").hidden = true;
  field("auth-title").textContent = register ? "Your next chapter." : "Welcome back.";
  field("auth-description").textContent = register ? "Create an account to keep your practice and progress together." : "Sign in to your résumé interviews, study notes, and saved progress.";
  field("submit-auth").textContent = register ? "Create account" : "Sign in";
  field("toggle-auth").textContent = register ? "I already have an account" : "Create an account";
  field("password").autocomplete = register ? "new-password" : "current-password";
  field("password").minLength = register ? 12 : 1;
  field("password-help").hidden = !register; field("auth-status").textContent = "";
});
field("auth-form").addEventListener("submit", async event => {
  event.preventDefault(); if (busy) return; busy = true;
  field("auth-error").hidden = true; field("auth-status").textContent = register ? "Creating your account…" : "Signing in…";
  document.querySelectorAll("button,input").forEach(element => element.disabled = true);
  try {
    await prepare();
    const credentials = { username: field("username").value, password: field("password").value };
    if (register) await accountAction("register", credentials);
    await accountAction("login", credentials);
    field("password").value = "";
    location.assign(safeNext(new URL(location.href).searchParams.get("next")));
  } catch (error) {
    field("auth-error").textContent = error instanceof TypeError ? "Could not reach the server. Please try again." : error.message;
    field("auth-error").hidden = false; field("auth-status").textContent = "";
  } finally {
    busy = false; document.querySelectorAll("button,input").forEach(element => element.disabled = false);
  }
});
