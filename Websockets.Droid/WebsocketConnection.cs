using System;
using System.Collections.Generic;
using Android.Runtime;
using Websockets.DroidBridge;
using Javax.Xml.Transform;

namespace Websockets.Droid
{
    /// <summary>
    /// A Websocket connection via androidBridge application
    /// </summary>
    public class WebsocketConnection : BridgeProxy, IWebSocketConnection
    {
        public bool IsOpen { get; private set; }

        public event Action OnClosed = delegate { };
        public event Action OnOpened = delegate { };
        public event Action<IWebSocketConnection> OnDispose = delegate { };
        public event Action<string> OnError = delegate { };
        public event Action<string> OnMessage = delegate { };
        public event Action<byte[]> OnData = delegate { };
        public event Action<string> OnLog = delegate { };

        private BridgeController _controller;

        public WebsocketConnection()
        {
        }

		//fixes {System.MissingMethodException: No constructor found for Websockets.Droid.WebsocketConnection::.ctor(System.IntPtr, Android.Runtime.JniHandleOwnership) 
		public WebsocketConnection(IntPtr javaReference, JniHandleOwnership transfer) : base(javaReference, transfer)
        {
		}
        
        /// <summary>
        /// Factory Initializer
        /// </summary>
        public static void Link()
        {
            System.Net.ServicePointManager.ServerCertificateValidationCallback += (o, certificate, chain, errors) => true;
            WebSocketFactory.Init(() => new WebsocketConnection());
        }

        public void Close()
        {
            try
            {
				IsOpen = false;
                if (_controller != null)
                {
                    _controller.Close();                   
                }
            }
            catch (Exception ex)
            {
                OnError(ex.Message);
            }
        }

        public void Open(string url, string protocol = null, string authToken = null)
        {
            var headers = new Dictionary<string, string>(StringComparer.InvariantCultureIgnoreCase);
            if (authToken != null)
            {
                headers.Add("Authorization", authToken);
            }

            Open(url, protocol, headers);
        }

        public void Open(string url, string protocol, IDictionary<string, string> headers)
        {
            try
            {
                _controller = new BridgeController();
                _controller.Proxy = this;
                _controller.Open(url, protocol, headers);
            }
            catch (Exception ex)
            {
                OnError(ex.Message);
            }
        }

        protected override void Dispose(bool disposing)
        {
            try
            {
                Close();

                if (_controller != null)
                {
                    _controller.Dispose();
                    _controller = null;
                }

                OnDispose(this);
                base.Dispose(disposing);
            }
            catch (Exception ex)
            {
                OnError(ex.Message);
            }
        }


        public void Send(string message)
        {
            try
            {
                _controller.Send(message);
            }
            catch (Exception ex)
            {
                OnError(ex.Message);
            }
        }

        public override unsafe void RaiseClosed()
        {
            IsOpen = false;
            OnClosed();
            base.RaiseClosed();
        }

        public override unsafe void RaiseError(string p1)
        {
            OnError(p1);
            base.RaiseError(p1);
        }

        public override unsafe void RaiseLog(string p1)
        {
            OnLog(p1);
            base.RaiseLog(p1);
        }

        public override unsafe void RaiseMessage(string p1)
        {
            OnMessage(p1);
            base.RaiseMessage(p1);
        }

		//public override unsafe void RaiseData(byte[] p1)
		//{
		//	OnData(p1);
		//	base.RaiseData(p1);
		//}

        public override unsafe void RaiseOpened()
        {
            IsOpen = true;
            OnOpened();
            base.RaiseOpened();
        }
    }
}