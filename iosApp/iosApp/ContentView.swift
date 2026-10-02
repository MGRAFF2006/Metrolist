import MetrolistShared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    private let player = NativeAudioPlayer()
    private let session = NativeAccountSession()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(player: player, session: session)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea(.keyboard)
    }
}
