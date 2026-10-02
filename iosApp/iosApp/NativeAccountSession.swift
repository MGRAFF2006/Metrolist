import MetrolistShared
import Security
import UIKit
import WebKit

final class NativeAccountSession: NSObject, AccountSession, WKNavigationDelegate, UIAdaptivePresentationControllerDelegate {
    private let service = "com.metrolist.music.ios.session"
    private let account = "youtube-cookie"
    private var completion: SignInListener?
    private var navigation: UINavigationController?
    private var webView: WKWebView?
    private var finishing = false

    var cookie: String? {
        var query = keychainQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func signIn(onComplete: SignInListener) {
        guard completion == nil else { return }
        guard let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
              var presenter = scene.windows.first(where: \.isKeyWindow)?.rootViewController else {
            onComplete.onComplete(cookie: nil)
            return
        }
        while let presented = presenter.presentedViewController { presenter = presented }
        completion = onComplete
        finishing = false
        let controller = UIViewController()
        controller.title = "YouTube Music sign-in"
        let web = WKWebView(frame: .zero)
        web.navigationDelegate = self
        web.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
        controller.view = web
        controller.navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem: .cancel, target: self, action: #selector(cancel))
        controller.navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "Done", style: .done, target: self, action: #selector(finishSignIn)),
            UIBarButtonItem(title: "Import", style: .plain, target: self, action: #selector(importCookie))
        ]
        let navigation = UINavigationController(rootViewController: controller)
        self.navigation = navigation
        webView = web
        presenter.present(navigation, animated: true)
        navigation.presentationController?.delegate = self
        web.load(URLRequest(url: URL(string: "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F")!))
    }

    func signOut() {
        let status = SecItemDelete(keychainQuery as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            showError("The saved session could not be removed (Keychain error \(status)).")
            return
        }
        let store = WKWebsiteDataStore.default()
        store.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            let accountRecords = records.filter { record in
                ["youtube.com", "google.com"].contains { domain in
                    record.displayName == domain || record.displayName.hasSuffix("." + domain)
                }
            }
            store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: accountRecords) {}
        }
    }

    @objc private func finishSignIn() {
        guard !finishing, let webView else { return }
        finishing = true
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { [weak self] cookies in
            guard let self, self.webView === webView, self.completion != nil else { return }
            self.finishing = false
            let youtubeCookies = cookies.filter {
                let domain = $0.domain.trimmingCharacters(in: CharacterSet(charactersIn: "."))
                return (domain == "youtube.com" || domain == "music.youtube.com") &&
                    ($0.expiresDate == nil || $0.expiresDate! > Date())
            }
            guard youtubeCookies.contains(where: { ["SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID"].contains($0.name) }) else {
                self.showError("Finish signing in to YouTube Music, then tap Done.")
                return
            }
            let header = youtubeCookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
            guard self.save(header) else { return }
            self.finish(header)
        }
    }

    @objc private func importCookie() {
        let alert = UIAlertController(title: "Import YouTube Music session", message: "Paste a session cookie from a browser where you are signed in to YouTube Music.", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "Cookie"
            field.isSecureTextEntry = true
            field.autocorrectionType = .no
            field.autocapitalizationType = .none
        }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Import", style: .default) { [weak self, weak alert] _ in
            guard let self, let header = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
            let names = header.split(separator: ";").map { $0.split(separator: "=", maxSplits: 1).first?.trimmingCharacters(in: .whitespaces) }
            guard !header.contains("\r"), !header.contains("\n"),
                  names.contains(where: { ["SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID"].contains($0 ?? "") }) else {
                self.showError("This cookie does not contain a YouTube Music sign-in session.")
                return
            }
            if self.save(header) { self.finish(header) }
        })
        navigation?.present(alert, animated: true)
    }

    private func save(_ header: String) -> Bool {
        var query = keychainQuery
        query[kSecValueData as String] = Data(header.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let existing = SecItemUpdate(keychainQuery as CFDictionary, [kSecValueData as String: Data(header.utf8)] as CFDictionary)
        let status = existing == errSecItemNotFound ? SecItemAdd(query as CFDictionary, nil) : existing
        guard status == errSecSuccess else {
            showError("The session could not be saved (Keychain error \(status)).")
            return false
        }
        return true
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        self.navigation?.topViewController?.title = webView.url?.host ?? "YouTube Music sign-in"
    }

    @objc private func cancel() { finish(nil) }

    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) { finish(nil) }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        if (error as NSError).code != NSURLErrorCancelled { showError(error.localizedDescription) }
    }

    private var keychainQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    private func finish(_ cookie: String?) {
        let callback = completion
        completion = nil
        navigation?.dismiss(animated: true)
        navigation = nil
        webView?.navigationDelegate = nil
        webView = nil
        callback?.onComplete(cookie: cookie)
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: "Account", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        if let navigation {
            navigation.present(alert, animated: true)
        } else if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
            scene.windows.first(where: \.isKeyWindow)?.rootViewController?.present(alert, animated: true)
        }
    }
}
