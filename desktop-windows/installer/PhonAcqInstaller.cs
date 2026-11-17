using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("PhonAcq Setup")]
[assembly: AssemblyDescription("PhonAcq 田野语音与方言字表采录系统 安装程序")]
[assembly: AssemblyCompany("KasumiKitsune")]
[assembly: AssemblyProduct("PhonAcq")]
[assembly: AssemblyCopyright("Copyright (c) KasumiKitsune")]

namespace PhonAcqSetup
{
    internal static class NativeMethods
    {
        [DllImport("user32.dll")]
        public static extern bool ReleaseCapture();

        [DllImport("user32.dll")]
        public static extern IntPtr SendMessage(IntPtr hWnd, int msg, int wParam, int lParam);

        [DllImport("user32.dll")]
        public static extern bool SetProcessDPIAware();

        [DllImport("user32.dll")]
        public static extern bool SetProcessDpiAwarenessContext(IntPtr dpiFlag);

        [DllImport("dwmapi.dll")]
        public static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

        [DllImport("gdi32.dll")]
        public static extern IntPtr CreateRoundRectRgn(int left, int top, int right, int bottom, int widthEllipse, int heightEllipse);

        [DllImport("shell32.dll")]
        public static extern void SHChangeNotify(int wEventId, uint uFlags, IntPtr dwItem1, IntPtr dwItem2);

        public const int WM_NCLBUTTONDOWN = 0x00A1;
        public const int HTCAPTION = 2;
        public const int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        public const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        public const int DWMWCP_ROUND = 2;
        public const int SHCNE_ASSOCCHANGED = 0x08000000;
        public const uint SHCNF_IDLIST = 0x0000;

        public static void EnableHighDpi()
        {
            try
            {
                // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = -4
                SetProcessDpiAwarenessContext(new IntPtr(-4));
                return;
            }
            catch
            {
            }
            try
            {
                SetProcessDPIAware();
            }
            catch
            {
            }
        }

        public static bool IsSystemDarkTheme()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"))
                {
                    if (key != null)
                    {
                        object val = key.GetValue("AppsUseLightTheme");
                        if (val is int)
                        {
                            return ((int)val) == 0;
                        }
                    }
                }
            }
            catch
            {
            }
            return false;
        }
    }

    /// <summary>
    /// Modern Windows 10/11 COM IFileOpenDialog (FOS_PICKFOLDERS) for selecting the installation folder.
    /// </summary>
    internal static class ModernFolderBrowser
    {
        [ComImport]
        [Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
        private class FileOpenDialogRCW { }

        [ComImport]
        [Guid("42f85136-db7e-439c-85f1-e4075d135fc8")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IFileOpenDialog
        {
            [PreserveSig] int Show([In] IntPtr parent);
            void SetFileTypes();
            void SetFileTypeIndex([In] uint iFileType);
            void GetFileTypeIndex(out uint piFileType);
            void Advise();
            void Unadvise([In] uint dwCookie);
            void SetOptions([In] uint fos);
            void GetOptions(out uint pfos);
            void SetDefaultFolder([In, MarshalAs(UnmanagedType.Interface)] IShellItem psi);
            void SetFolder([In, MarshalAs(UnmanagedType.Interface)] IShellItem psi);
            void GetFolder([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
            void GetCurrentSelection([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
            void SetFileName([In, MarshalAs(UnmanagedType.LPWStr)] string pszName);
            void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
            void SetTitle([In, MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
            void SetOkButtonLabel([In, MarshalAs(UnmanagedType.LPWStr)] string pszText);
            void SetFileNameLabel([In, MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
            void GetResult([MarshalAs(UnmanagedType.Interface)] out IShellItem ppsi);
        }

        [ComImport]
        [Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IShellItem
        {
            void BindToHandler();
            void GetParent();
            void GetDisplayName([In] uint sigdnName, [MarshalAs(UnmanagedType.LPWStr)] out string ppszName);
            void GetAttributes();
            void Compare();
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern int SHCreateItemFromParsingName(
            [MarshalAs(UnmanagedType.LPWStr)] string pszPath,
            IntPtr pbc,
            ref Guid riid,
            [MarshalAs(UnmanagedType.Interface)] out IShellItem ppv);

        private const uint FOS_PICKFOLDERS = 0x00000020;
        private const uint FOS_FORCEFILESYSTEM = 0x00000040;
        private const uint FOS_NOREADONLYRETURN = 0x00008000;
        private const uint SIGDN_FILESYSPATH = 0x80058000;

        public static string PickFolder(IntPtr ownerHandle, string initialDirectory, string title)
        {
            try
            {
                IFileOpenDialog dialog = (IFileOpenDialog)new FileOpenDialogRCW();
                uint options;
                dialog.GetOptions(out options);
                dialog.SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_NOREADONLYRETURN);
                dialog.SetTitle(title);
                dialog.SetOkButtonLabel("选择文件夹");

                if (!string.IsNullOrEmpty(initialDirectory))
                {
                    string probe = initialDirectory;
                    while (!string.IsNullOrEmpty(probe) && !Directory.Exists(probe))
                    {
                        probe = Path.GetDirectoryName(probe);
                    }
                    if (!string.IsNullOrEmpty(probe) && Directory.Exists(probe))
                    {
                        Guid shellItemGuid = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
                        IShellItem folderItem;
                        if (SHCreateItemFromParsingName(probe, IntPtr.Zero, ref shellItemGuid, out folderItem) == 0 && folderItem != null)
                        {
                            dialog.SetFolder(folderItem);
                        }
                    }
                }

                int hr = dialog.Show(ownerHandle);
                if (hr == 0)
                {
                    IShellItem resultItem;
                    dialog.GetResult(out resultItem);
                    if (resultItem != null)
                    {
                        string selectedPath;
                        resultItem.GetDisplayName(SIGDN_FILESYSPATH, out selectedPath);
                        if (!string.IsNullOrEmpty(selectedPath))
                        {
                            return selectedPath;
                        }
                    }
                }
                return null;
            }
            catch
            {
                using (FolderBrowserDialog fallback = new FolderBrowserDialog())
                {
                    fallback.Description = title;
                    if (!string.IsNullOrEmpty(initialDirectory) && Directory.Exists(initialDirectory))
                    {
                        fallback.SelectedPath = initialDirectory;
                    }
                    if (fallback.ShowDialog() == DialogResult.OK)
                    {
                        return fallback.SelectedPath;
                    }
                }
                return null;
            }
        }
    }

    /// <summary>
    /// Authentic Google Material You (Material Design 3) Blue-Seed Tonal Palette.
    /// Surface hierarchy: tinted WindowSurface -> elevated SurfaceCard -> SurfaceContainer -> Primary.
    /// </summary>
    internal sealed class GoogleMaterialPalette
    {
        public readonly bool IsDark;
        public readonly Color WindowSurface;
        public readonly Color CardSurface;
        public readonly Color SurfaceContainer;
        public readonly Color SurfaceContainerHigh;
        public readonly Color SurfaceContainerHighest;
        public readonly Color InputSurface;
        public readonly Color Primary;
        public readonly Color PrimaryHover;
        public readonly Color OnPrimary;
        public readonly Color PrimaryContainer;
        public readonly Color PrimaryContainerHover;
        public readonly Color OnPrimaryContainer;
        public readonly Color SecondaryContainer;
        public readonly Color SecondaryContainerHover;
        public readonly Color OnSecondaryContainer;
        public readonly Color OnSurface;
        public readonly Color OnSurfaceVariant;
        public readonly Color Outline;
        public readonly Color OutlineVariant;
        public readonly Color WindowBorder;
        public readonly Color Error;
        public readonly Color ErrorHover;
        public readonly Color OnError;
        public readonly Color ErrorContainer;
        public readonly Color OnErrorContainer;
        public readonly Color StateLayerHover;
        public readonly Color RippleLight;
        public readonly Color RippleDark;

        public GoogleMaterialPalette(bool dark)
        {
            IsDark = dark;
            if (dark)
            {
                WindowSurface = Color.FromArgb(19, 19, 20);              // #131314 Google Dark Surface
                CardSurface = Color.FromArgb(30, 31, 34);                // #1E1F22 Google Dark SurfaceContainer
                SurfaceContainer = Color.FromArgb(40, 42, 46);           // #282A2E SurfaceContainerHigh
                SurfaceContainerHigh = Color.FromArgb(51, 53, 58);       // #33353A SurfaceContainerHighest
                SurfaceContainerHighest = Color.FromArgb(68, 71, 70);    // #444746
                InputSurface = Color.FromArgb(19, 19, 20);               // #131314
                Primary = Color.FromArgb(168, 199, 250);                 // #A8C7FA Google Blue 200
                PrimaryHover = Color.FromArgb(189, 214, 253);            // #BDD6FD
                OnPrimary = Color.FromArgb(6, 46, 111);                  // #062E6F
                PrimaryContainer = Color.FromArgb(8, 66, 160);           // #0842A0
                PrimaryContainerHover = Color.FromArgb(11, 87, 208);     // #0B57D0
                OnPrimaryContainer = Color.FromArgb(211, 227, 253);      // #D3E3FD
                SecondaryContainer = Color.FromArgb(0, 74, 119);         // #004A77
                SecondaryContainerHover = Color.FromArgb(0, 99, 155);    // #00639B
                OnSecondaryContainer = Color.FromArgb(194, 231, 255);    // #C2E7FF
                OnSurface = Color.FromArgb(227, 227, 227);               // #E3E3E3
                OnSurfaceVariant = Color.FromArgb(196, 199, 197);        // #C4C7C5
                Outline = Color.FromArgb(142, 145, 143);                 // #8E918F
                OutlineVariant = Color.FromArgb(68, 71, 70);             // #444746
                WindowBorder = Color.FromArgb(68, 71, 70);               // #444746
                Error = Color.FromArgb(242, 184, 181);                   // #F2B8B5
                ErrorHover = Color.FromArgb(249, 222, 220);              // #F9DEDC
                OnError = Color.FromArgb(96, 20, 16);                    // #601410
                ErrorContainer = Color.FromArgb(140, 29, 24);            // #8C1D18
                OnErrorContainer = Color.FromArgb(249, 222, 220);        // #F9DEDC
                StateLayerHover = Color.FromArgb(20, 227, 227, 227);
                RippleLight = Color.FromArgb(64, 255, 255, 255);
                RippleDark = Color.FromArgb(52, 168, 199, 250);
            }
            else
            {
                WindowSurface = Color.FromArgb(240, 244, 249);           // #F0F4F9 Google M3 Tinted Surface
                CardSurface = Color.FromArgb(255, 255, 255);             // #FFFFFF Elevated Card
                SurfaceContainer = Color.FromArgb(248, 250, 253);        // #F8FAFD Input / Sub-surface
                SurfaceContainerHigh = Color.FromArgb(233, 238, 246);    // #E9EEF6 SurfaceContainerHigh
                SurfaceContainerHighest = Color.FromArgb(221, 227, 234); // #DDE3EA Switch unchecked track
                InputSurface = Color.FromArgb(248, 250, 253);            // #F8FAFD
                Primary = Color.FromArgb(11, 87, 208);                   // #0B57D0 Google Blue 600
                PrimaryHover = Color.FromArgb(8, 76, 184);               // #084CB8
                OnPrimary = Color.FromArgb(255, 255, 255);               // #FFFFFF
                PrimaryContainer = Color.FromArgb(211, 227, 253);        // #D3E3FD
                PrimaryContainerHover = Color.FromArgb(194, 215, 251);   // #C2D7FB
                OnPrimaryContainer = Color.FromArgb(4, 30, 73);          // #041E49
                SecondaryContainer = Color.FromArgb(194, 231, 255);      // #C2E7FF Google Pill / Tonal
                SecondaryContainerHover = Color.FromArgb(174, 220, 252); // #AEDCFC
                OnSecondaryContainer = Color.FromArgb(0, 29, 53);        // #001D35
                OnSurface = Color.FromArgb(31, 31, 31);                  // #1F1F1F
                OnSurfaceVariant = Color.FromArgb(68, 71, 70);           // #444746
                Outline = Color.FromArgb(116, 119, 117);                 // #747775
                OutlineVariant = Color.FromArgb(224, 226, 224);          // #E0E2E0
                WindowBorder = Color.FromArgb(211, 218, 229);            // #D3DAE5
                Error = Color.FromArgb(179, 38, 30);                     // #B3261E Google M3 Error
                ErrorHover = Color.FromArgb(156, 30, 23);                // #9C1E17
                OnError = Color.FromArgb(255, 255, 255);                 // #FFFFFF
                ErrorContainer = Color.FromArgb(249, 222, 220);          // #F9DEDC
                OnErrorContainer = Color.FromArgb(65, 14, 11);           // #410E0B
                StateLayerHover = Color.FromArgb(18, 11, 87, 208);
                RippleLight = Color.FromArgb(68, 255, 255, 255);
                RippleDark = Color.FromArgb(46, 11, 87, 208);
            }
        }
    }

    internal sealed class RippleEffect
    {
        public Point Origin;
        public Rectangle ClipBounds;
        public int CornerRadius;
        public float Progress;
        public float Alpha;
        public bool IsHolding;
        public bool LightRipple;
    }

    /// <summary>
    /// Read-only Seekable window over the appended ZIP payload inside the self-extracting setup .exe.
    /// </summary>
    internal sealed class EmbeddedPayloadStream : Stream
    {
        private readonly FileStream _baseStream;
        private readonly long _offset;
        private readonly long _length;
        private long _position;

        public EmbeddedPayloadStream(FileStream baseStream, long offset, long length)
        {
            _baseStream = baseStream;
            _offset = offset;
            _length = length;
            _position = 0;
            _baseStream.Seek(_offset, SeekOrigin.Begin);
        }

        public override bool CanRead { get { return true; } }
        public override bool CanSeek { get { return true; } }
        public override bool CanWrite { get { return false; } }
        public override long Length { get { return _length; } }

        public override long Position
        {
            get { return _position; }
            set { Seek(value, SeekOrigin.Begin); }
        }

        public override void Flush() { }

        public override int Read(byte[] buffer, int offset, int count)
        {
            long remaining = _length - _position;
            if (remaining <= 0) return 0;
            if (count > remaining) count = (int)remaining;
            _baseStream.Seek(_offset + _position, SeekOrigin.Begin);
            int read = _baseStream.Read(buffer, offset, count);
            _position += read;
            return read;
        }

        public override long Seek(long offset, SeekOrigin origin)
        {
            long nextPos;
            switch (origin)
            {
                case SeekOrigin.Begin:
                    nextPos = offset;
                    break;
                case SeekOrigin.Current:
                    nextPos = _position + offset;
                    break;
                case SeekOrigin.End:
                    nextPos = _length + offset;
                    break;
                default:
                    throw new ArgumentOutOfRangeException("origin");
            }
            if (nextPos < 0) nextPos = 0;
            if (nextPos > _length) nextPos = _length;
            _position = nextPos;
            _baseStream.Seek(_offset + _position, SeekOrigin.Begin);
            return _position;
        }

        public override void SetLength(long value) { throw new NotSupportedException(); }
        public override void Write(byte[] buffer, int offset, int count) { throw new NotSupportedException(); }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                _baseStream.Dispose();
            }
            base.Dispose(disposing);
        }
    }

    internal sealed class PayloadDescriptor
    {
        public long PayloadOffset;
        public long PayloadLength;
        public long StubLength;
        public string AppVersion;
    }

    internal static class InstallerEngine
    {
        public const string APP_NAME = "PhonAcq";
        public const string PUBLISHER = "KasumiKitsune";
        public const string UNINSTALL_REG_SUBKEY = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\PhonAcq";
        public static readonly byte[] TRAILER_MAGIC = Encoding.ASCII.GetBytes("PAQSETUP");

        public static PayloadDescriptor ReadPayloadDescriptor(string exePath, string fallbackVersion)
        {
            FileInfo fi = new FileInfo(exePath);
            if (!fi.Exists || fi.Length < 64) return null;

            using (FileStream fs = new FileStream(exePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            {
                fs.Seek(-32, SeekOrigin.End);
                byte[] trailer = new byte[32];
                int read = fs.Read(trailer, 0, 32);
                if (read != 32) return null;

                for (int i = 0; i < 8; i++)
                {
                    if (trailer[i] != TRAILER_MAGIC[i]) return null;
                }

                long payloadOffset = BitConverter.ToInt64(trailer, 8);
                long payloadLength = BitConverter.ToInt64(trailer, 16);
                long stubLength = BitConverter.ToInt64(trailer, 24);
                if (payloadOffset <= 0 || payloadLength <= 0 || payloadOffset + payloadLength + 32 > fi.Length)
                {
                    return null;
                }

                PayloadDescriptor desc = new PayloadDescriptor();
                desc.PayloadOffset = payloadOffset;
                desc.PayloadLength = payloadLength;
                desc.StubLength = stubLength > 0 ? stubLength : payloadOffset;
                desc.AppVersion = fallbackVersion;
                return desc;
            }
        }

        public static void DetectExistingInstallation(
            out string existingDir,
            out string existingVersion,
            out List<string> legacyMsiProductCodes)
        {
            existingDir = null;
            existingVersion = null;
            legacyMsiProductCodes = new List<string>();

            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UNINSTALL_REG_SUBKEY))
                {
                    if (key != null)
                    {
                        string loc = key.GetValue("InstallLocation") as string;
                        string ver = key.GetValue("DisplayVersion") as string;
                        if (!string.IsNullOrEmpty(loc)) existingDir = loc;
                        if (!string.IsNullOrEmpty(ver)) existingVersion = ver;
                    }
                }
            }
            catch
            {
            }

            try
            {
                using (RegistryKey uninstallRoot = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall"))
                {
                    if (uninstallRoot != null)
                    {
                        foreach (string subName in uninstallRoot.GetSubKeyNames())
                        {
                            if (string.Equals(subName, APP_NAME, StringComparison.OrdinalIgnoreCase)) continue;
                            using (RegistryKey sub = uninstallRoot.OpenSubKey(subName))
                            {
                                if (sub == null) continue;
                                string dispName = sub.GetValue("DisplayName") as string;
                                if (!string.Equals(dispName, APP_NAME, StringComparison.OrdinalIgnoreCase)) continue;

                                string uninst = sub.GetValue("UninstallString") as string;
                                string loc = sub.GetValue("InstallLocation") as string;
                                string ver = sub.GetValue("DisplayVersion") as string;

                                if (string.IsNullOrEmpty(existingVersion) && !string.IsNullOrEmpty(ver))
                                {
                                    existingVersion = ver;
                                }
                                if (string.IsNullOrEmpty(existingDir) && !string.IsNullOrEmpty(loc))
                                {
                                    existingDir = loc.TrimEnd('\\');
                                }

                                if (!string.IsNullOrEmpty(uninst) &&
                                    uninst.IndexOf("MsiExec", StringComparison.OrdinalIgnoreCase) >= 0 &&
                                    subName.StartsWith("{") && subName.EndsWith("}"))
                                {
                                    legacyMsiProductCodes.Add(subName);
                                }
                            }
                        }
                    }
                }
            }
            catch
            {
            }

            if (string.IsNullOrEmpty(existingDir))
            {
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string modernDefault = Path.Combine(localAppData, "Programs", APP_NAME);
                string legacyMsiDefault = Path.Combine(localAppData, APP_NAME);

                if (File.Exists(Path.Combine(modernDefault, "PhonAcq.exe")))
                {
                    existingDir = modernDefault;
                }
                else if (File.Exists(Path.Combine(legacyMsiDefault, "PhonAcq.exe")))
                {
                    existingDir = legacyMsiDefault;
                }
            }
        }

        public static string GetDefaultInstallDir(string detectedDir)
        {
            if (!string.IsNullOrEmpty(detectedDir))
            {
                return detectedDir;
            }
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localAppData, "Programs", APP_NAME);
        }

        public static void StopRunningPhonAcqProcesses()
        {
            int selfPid = Process.GetCurrentProcess().Id;
            try
            {
                Process[] procs = Process.GetProcessesByName("PhonAcq");
                foreach (Process p in procs)
                {
                    try
                    {
                        if (p.Id == selfPid) continue;
                        if (p.CloseMainWindow())
                        {
                            if (p.WaitForExit(2200)) continue;
                        }
                        if (!p.HasExited)
                        {
                            p.Kill();
                            p.WaitForExit(2000);
                        }
                    }
                    catch
                    {
                    }
                    finally
                    {
                        p.Dispose();
                    }
                }
            }
            catch
            {
            }
        }

        public static void CleanupLegacyPerUserMsi(List<string> productCodes)
        {
            if (productCodes == null || productCodes.Count == 0) return;
            foreach (string code in productCodes)
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = "msiexec.exe";
                    psi.Arguments = String.Format("/x {0} /qn /norestart", code);
                    psi.CreateNoWindow = true;
                    psi.UseShellExecute = false;
                    using (Process p = Process.Start(psi))
                    {
                        if (p != null) p.WaitForExit(15000);
                    }
                }
                catch
                {
                }
            }
        }

        /// <summary>
        /// PhonAcq intentionally does NOT associate .teproj (which belongs to PhonTracer).
        /// If an earlier PhonAcq build wrote a PhonAcq.teproj key, clean only that entry without touching PhonTracer.
        /// </summary>
        public static void CleanupLegacyPhonAcqTeprojAssociation()
        {
            try
            {
                bool changed = false;
                using (RegistryKey progKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\PhonAcq.teproj"))
                {
                    if (progKey != null) changed = true;
                }
                if (changed)
                {
                    Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\PhonAcq.teproj", false);
                }

                bool removeDotKey = false;
                using (RegistryKey extKey = Registry.CurrentUser.OpenSubKey(@"Software\Classes\.teproj", false))
                {
                    if (extKey != null)
                    {
                        string cur = extKey.GetValue("") as string;
                        if (string.Equals(cur, "PhonAcq.teproj", StringComparison.OrdinalIgnoreCase))
                        {
                            removeDotKey = true;
                        }
                    }
                }
                if (removeDotKey)
                {
                    Registry.CurrentUser.DeleteSubKeyTree(@"Software\Classes\.teproj", false);
                    changed = true;
                }

                if (changed)
                {
                    NativeMethods.SHChangeNotify(NativeMethods.SHCNE_ASSOCCHANGED, NativeMethods.SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
                }
            }
            catch
            {
            }
        }

        public static void CreateShortcut(string shortcutPath, string targetExe, string workingDir, string description)
        {
            try
            {
                string parent = Path.GetDirectoryName(shortcutPath);
                if (!string.IsNullOrEmpty(parent) && !Directory.Exists(parent))
                {
                    Directory.CreateDirectory(parent);
                }

                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                object shell = Activator.CreateInstance(shellType);
                try
                {
                    object shortcut = shellType.InvokeMember(
                        "CreateShortcut",
                        BindingFlags.InvokeMethod,
                        null,
                        shell,
                        new object[] { shortcutPath });
                    if (shortcut == null) return;
                    try
                    {
                        Type scType = shortcut.GetType();
                        scType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe });
                        scType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDir });
                        scType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { targetExe + ",0" });
                        scType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
                        scType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
                    }
                    finally
                    {
                        Marshal.FinalReleaseComObject(shortcut);
                    }
                }
                finally
                {
                    Marshal.FinalReleaseComObject(shell);
                }
            }
            catch
            {
            }
        }

        public static void RegisterUninstaller(string installDir, string targetExe, string uninstallerExe, string version, long totalBytes)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UNINSTALL_REG_SUBKEY))
                {
                    if (key == null) return;
                    key.SetValue("DisplayName", APP_NAME);
                    key.SetValue("DisplayVersion", version);
                    key.SetValue("Publisher", PUBLISHER);
                    key.SetValue("InstallLocation", installDir);
                    key.SetValue("DisplayIcon", targetExe);
                    key.SetValue("UninstallString", String.Format("\"{0}\" --uninstall", uninstallerExe));
                    key.SetValue("QuietUninstallString", String.Format("\"{0}\" --uninstall --silent", uninstallerExe));
                    key.SetValue("InstallDate", DateTime.Now.ToString("yyyyMMdd"));
                    key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                    key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                    int sizeKb = (int)Math.Max(1024L, totalBytes / 1024L);
                    key.SetValue("EstimatedSize", sizeKb, RegistryValueKind.DWord);
                }
            }
            catch
            {
            }
        }

        public static void PerformInstall(
            string setupExePath,
            PayloadDescriptor desc,
            string installDir,
            bool createDesktopShortcut,
            bool createStartMenuShortcut,
            List<string> legacyMsiCodes,
            Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(4, "正在关闭运行中的 PhonAcq...");
            StopRunningPhonAcqProcesses();

            if (legacyMsiCodes != null && legacyMsiCodes.Count > 0)
            {
                if (onProgress != null) onProgress(9, "正在更新旧版安装记录...");
                CleanupLegacyPerUserMsi(legacyMsiCodes);
            }

            if (!Directory.Exists(installDir))
            {
                Directory.CreateDirectory(installDir);
            }

            string oldAppSubdir = Path.Combine(installDir, "app");
            string oldRuntimeSubdir = Path.Combine(installDir, "runtime");
            string oldLibSubdir = Path.Combine(installDir, "lib");
            try
            {
                if (Directory.Exists(oldAppSubdir)) Directory.Delete(oldAppSubdir, true);
                if (Directory.Exists(oldRuntimeSubdir)) Directory.Delete(oldRuntimeSubdir, true);
                if (Directory.Exists(oldLibSubdir)) Directory.Delete(oldLibSubdir, true);
            }
            catch
            {
            }

            long totalExtractedBytes = 0;
            using (FileStream fs = new FileStream(setupExePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (EmbeddedPayloadStream sub = new EmbeddedPayloadStream(fs, desc.PayloadOffset, desc.PayloadLength))
            using (ZipArchive archive = new ZipArchive(sub, ZipArchiveMode.Read))
            {
                int totalEntries = Math.Max(1, archive.Entries.Count);
                int index = 0;
                byte[] copyBuf = new byte[81920];

                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    index++;
                    string relPath = entry.FullName.Replace('/', Path.DirectorySeparatorChar);
                    if (string.IsNullOrEmpty(relPath)) continue;

                    string fullDest = Path.GetFullPath(Path.Combine(installDir, relPath));
                    string rootPrefix = Path.GetFullPath(installDir);
                    if (!rootPrefix.EndsWith(Path.DirectorySeparatorChar.ToString()))
                    {
                        rootPrefix += Path.DirectorySeparatorChar;
                    }
                    if (!fullDest.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }

                    if (string.IsNullOrEmpty(entry.Name) && (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\")))
                    {
                        if (!Directory.Exists(fullDest)) Directory.CreateDirectory(fullDest);
                        continue;
                    }

                    string parentDir = Path.GetDirectoryName(fullDest);
                    if (!string.IsNullOrEmpty(parentDir) && !Directory.Exists(parentDir))
                    {
                        Directory.CreateDirectory(parentDir);
                    }

                    using (Stream inStream = entry.Open())
                    using (FileStream outStream = new FileStream(fullDest, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        int r;
                        while ((r = inStream.Read(copyBuf, 0, copyBuf.Length)) > 0)
                        {
                            outStream.Write(copyBuf, 0, r);
                            totalExtractedBytes += r;
                        }
                    }

                    int pct = 12 + (int)((index * 78L) / totalEntries);
                    if (index == 1 || index % 8 == 0 || index == totalEntries)
                    {
                        if (onProgress != null)
                        {
                            onProgress(pct, "正在复制程序文件...");
                        }
                    }
                }
            }

            if (onProgress != null) onProgress(93, "正在注册应用信息...");

            string uninstallerExe = Path.Combine(installDir, "Uninstall-PhonAcq.exe");
            using (FileStream srcStub = new FileStream(setupExePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (FileStream dstStub = new FileStream(uninstallerExe, FileMode.Create, FileAccess.Write, FileShare.None))
            {
                long remaining = desc.StubLength;
                byte[] buf = new byte[65536];
                while (remaining > 0)
                {
                    int chunk = (int)Math.Min(buf.Length, remaining);
                    int n = srcStub.Read(buf, 0, chunk);
                    if (n <= 0) break;
                    dstStub.Write(buf, 0, n);
                    remaining -= n;
                }
            }

            string mainExe = Path.Combine(installDir, "PhonAcq.exe");
            RegisterUninstaller(installDir, mainExe, uninstallerExe, desc.AppVersion, totalExtractedBytes);
            CleanupLegacyPhonAcqTeprojAssociation();

            if (onProgress != null) onProgress(97, "正在创建快捷方式...");

            string descText = "PhonAcq 田野语音与方言字表采录系统";
            if (createDesktopShortcut)
            {
                string desktopDir = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                CreateShortcut(Path.Combine(desktopDir, "PhonAcq.lnk"), mainExe, installDir, descText);
            }
            if (createStartMenuShortcut)
            {
                string programsDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                string groupDir = Path.Combine(programsDir, APP_NAME);
                CreateShortcut(Path.Combine(groupDir, "PhonAcq.lnk"), mainExe, installDir, descText);
            }

            if (onProgress != null) onProgress(100, "安装完成");
        }

        public static void PerformUninstall(string installDir, string selfExePath, Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(15, "正在关闭运行中的 PhonAcq...");
            StopRunningPhonAcqProcesses();

            if (onProgress != null) onProgress(45, "正在移除快捷方式...");
            try
            {
                string desktopLnk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "PhonAcq.lnk");
                if (File.Exists(desktopLnk)) File.Delete(desktopLnk);

                string startMenuGroup = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), APP_NAME);
                if (Directory.Exists(startMenuGroup)) Directory.Delete(startMenuGroup, true);
            }
            catch
            {
            }

            if (onProgress != null) onProgress(70, "正在清理应用注册信息...");
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(UNINSTALL_REG_SUBKEY, false);
            }
            catch
            {
            }
            CleanupLegacyPhonAcqTeprojAssociation();

            if (onProgress != null) onProgress(88, "正在移除程序文件...");
            if (!string.IsNullOrEmpty(installDir) && Directory.Exists(installDir))
            {
                foreach (string subDir in Directory.GetDirectories(installDir))
                {
                    try { Directory.Delete(subDir, true); } catch { }
                }
                foreach (string file in Directory.GetFiles(installDir))
                {
                    if (string.Equals(Path.GetFullPath(file), Path.GetFullPath(selfExePath), StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }
                    try { File.Delete(file); } catch { }
                }

                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = "cmd.exe";
                    psi.Arguments = String.Format("/c ping 127.0.0.1 -n 2 > nul & del /f /q \"{0}\" & rmdir \"{1}\"", selfExePath, installDir);
                    psi.CreateNoWindow = true;
                    psi.WindowStyle = ProcessWindowStyle.Hidden;
                    psi.UseShellExecute = false;
                    Process.Start(psi);
                }
                catch
                {
                }
            }

            if (onProgress != null) onProgress(100, "卸载完成");
        }
    }

    internal enum SetupStage
    {
        Ready,
        Working,
        Completed,
        Error
    }

    internal sealed class PhonAcqInstallerForm : Form
    {
        private readonly bool _isUninstallMode;
        private readonly string _appVersion;
        private readonly PayloadDescriptor _payload;
        private readonly Image _logoImage;
        private readonly GoogleMaterialPalette _theme;

        private string _installDir;
        private readonly string _existingVersion;
        private readonly bool _isOverwriteUpgrade;
        private readonly List<string> _legacyMsiCodes;

        private SetupStage _stage = SetupStage.Ready;
        private bool _optDesktopShortcut = true;
        private bool _optStartMenuShortcut = true;

        private int _progressPercent = 0;
        private float _visualProgress = 0f;
        private string _statusMessage = "";
        private string _errorMessage = "";

        private float _dpiScale = 1.0f;
        private bool _hasDwmRound = false;
        private readonly TextBox _pathTextBox;

        private Rectangle _rectMinBtn;
        private Rectangle _rectCloseBtn;
        private Rectangle _rectPrimaryBtn;
        private Rectangle _rectSecondaryBtn;
        private Rectangle _rectBrowseBtn;
        private Rectangle _rectRowDesktop;
        private Rectangle _rectRowStartMenu;

        private string _hoveredId = null;
        private string _pressedId = null;

        // 60fps Material You Animation State
        private readonly System.Windows.Forms.Timer _animTimer;
        private long _lastTickMs;
        private readonly Dictionary<string, float> _hoverProgress = new Dictionary<string, float>();
        private readonly Dictionary<string, float> _pressProgress = new Dictionary<string, float>();
        private float _desktopSwitchAnim = 1.0f;
        private float _startSwitchAnim = 1.0f;
        private float _inputFocusAnim = 0.0f;
        private float _stageTransitionAnim = 1.0f;
        private float _completeCheckAnim = 0.0f;
        private float _progressShimmerPhase = 0.0f;
        private readonly List<RippleEffect> _ripples = new List<RippleEffect>();

        public PhonAcqInstallerForm(bool isUninstallMode, string appVersion, PayloadDescriptor payload)
        {
            _isUninstallMode = isUninstallMode;
            _appVersion = appVersion;
            _payload = payload;
            _theme = new GoogleMaterialPalette(NativeMethods.IsSystemDarkTheme());

            SetStyle(
                ControlStyles.AllPaintingInWmPaint |
                ControlStyles.UserPaint |
                ControlStyles.OptimizedDoubleBuffer |
                ControlStyles.ResizeRedraw,
                true);

            AutoScaleMode = AutoScaleMode.None;
            ControlBox = false;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = _theme.WindowSurface;
            Text = isUninstallMode ? "卸载 PhonAcq" : "安装 PhonAcq";

            try
            {
                Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch
            {
            }

            _logoImage = LoadEmbeddedLogo();

            string detectedDir;
            string detectedVer;
            List<string> msiCodes;
            InstallerEngine.DetectExistingInstallation(out detectedDir, out detectedVer, out msiCodes);
            _existingVersion = detectedVer;
            _legacyMsiCodes = msiCodes;

            if (_isUninstallMode)
            {
                string exeDir = Path.GetDirectoryName(Application.ExecutablePath);
                _installDir = !string.IsNullOrEmpty(detectedDir) ? detectedDir : exeDir;
                _isOverwriteUpgrade = false;
            }
            else
            {
                _installDir = InstallerEngine.GetDefaultInstallDir(detectedDir);
                _isOverwriteUpgrade = !string.IsNullOrEmpty(detectedDir) ||
                                      !string.IsNullOrEmpty(detectedVer) ||
                                      (_legacyMsiCodes != null && _legacyMsiCodes.Count > 0);
            }

            _pathTextBox = new TextBox();
            _pathTextBox.BorderStyle = BorderStyle.None;
            _pathTextBox.BackColor = _theme.InputSurface;
            _pathTextBox.ForeColor = _theme.OnSurface;
            _pathTextBox.Text = _installDir;
            _pathTextBox.Visible = false;
            _pathTextBox.TextChanged += delegate { _installDir = _pathTextBox.Text.Trim(); };
            _pathTextBox.GotFocus += delegate { EnsureAnimRunning(); Invalidate(); };
            _pathTextBox.LostFocus += delegate { EnsureAnimRunning(); Invalidate(); };
            Controls.Add(_pathTextBox);

            _lastTickMs = Environment.TickCount & 0x7FFFFFFF;
            _animTimer = new System.Windows.Forms.Timer();
            _animTimer.Interval = 16;
            _animTimer.Tick += OnAnimTick;
        }

        private static Image LoadEmbeddedLogo()
        {
            try
            {
                Assembly asm = Assembly.GetExecutingAssembly();
                foreach (string resName in asm.GetManifestResourceNames())
                {
                    if (resName.EndsWith("phonacq-logo.png", StringComparison.OrdinalIgnoreCase))
                    {
                        Stream s = asm.GetManifestResourceStream(resName);
                        if (s != null) return Image.FromStream(s);
                    }
                }
            }
            catch
            {
            }
            return null;
        }

        protected override void OnHandleCreated(EventArgs e)
        {
            base.OnHandleCreated(e);
            using (Graphics g = CreateGraphics())
            {
                _dpiScale = Math.Max(1.0f, g.DpiX / 96.0f);
            }
            ApplyWindowGeometry();
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            if (_pathTextBox != null && _pathTextBox.Visible)
            {
                _pathTextBox.SelectionLength = 0;
                _pathTextBox.SelectionStart = _pathTextBox.Text.Length;
            }
            ActiveControl = null;
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (_animTimer != null)
            {
                _animTimer.Stop();
                _animTimer.Dispose();
            }
            base.OnFormClosed(e);
        }

        private int S(float logicalPx)
        {
            return (int)Math.Round(logicalPx * _dpiScale);
        }

        private float Sf(float logicalPx)
        {
            return logicalPx * _dpiScale;
        }

        private void ApplyWindowGeometry()
        {
            int w = S(516);
            int h = S(384);
            MinimumSize = Size.Empty;
            MaximumSize = Size.Empty;
            Size = new Size(w, h);
            MinimumSize = new Size(w, h);
            MaximumSize = new Size(w, h);

            if (_theme.IsDark)
            {
                try
                {
                    int darkPref = 1;
                    NativeMethods.DwmSetWindowAttribute(
                        Handle,
                        NativeMethods.DWMWA_USE_IMMERSIVE_DARK_MODE,
                        ref darkPref,
                        sizeof(int));
                }
                catch
                {
                }
            }

            int cornerPref = NativeMethods.DWMWCP_ROUND;
            int hr = -1;
            try
            {
                hr = NativeMethods.DwmSetWindowAttribute(
                    Handle,
                    NativeMethods.DWMWA_WINDOW_CORNER_PREFERENCE,
                    ref cornerPref,
                    sizeof(int));
            }
            catch
            {
            }
            _hasDwmRound = (hr == 0);
            if (!_hasDwmRound)
            {
                IntPtr rgn = NativeMethods.CreateRoundRectRgn(0, 0, w + 1, h + 1, S(24), S(24));
                Region = Region.FromHrgn(rgn);
            }

            LayoutPathTextBox();
            Invalidate();
        }

        private void LayoutPathTextBox()
        {
            bool showPath = !_isUninstallMode && _stage == SetupStage.Ready;
            _pathTextBox.Visible = showPath;
            if (showPath)
            {
                _pathTextBox.Font = new Font("Microsoft YaHei UI", Sf(12.5f), FontStyle.Regular, GraphicsUnit.Pixel);
                int boxX = S(44);
                int boxY = S(138);
                int boxW = S(342);
                int boxH = S(40);
                _pathTextBox.Location = new Point(boxX + S(14), boxY + (boxH - _pathTextBox.PreferredHeight) / 2);
                _pathTextBox.Width = boxW - S(28);
            }
        }

        private static GraphicsPath CreateRoundRect(Rectangle r, int radius)
        {
            GraphicsPath path = new GraphicsPath();
            int d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            if (d <= 0)
            {
                path.AddRectangle(r);
                return path;
            }
            path.AddArc(r.X, r.Y, d, d, 180, 90);
            path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }

        private static Color LerpColor(Color a, Color b, float t)
        {
            t = Math.Max(0f, Math.Min(1f, t));
            return Color.FromArgb(
                (int)Math.Round(a.A + (b.A - a.A) * t),
                (int)Math.Round(a.R + (b.R - a.R) * t),
                (int)Math.Round(a.G + (b.G - a.G) * t),
                (int)Math.Round(a.B + (b.B - a.B) * t));
        }

        private static float EaseOutCubic(float t)
        {
            t = Math.Max(0f, Math.Min(1f, t));
            float inv = 1f - t;
            return 1f - inv * inv * inv;
        }

        private static float Approach(float current, float target, float speed, float dt)
        {
            float diff = target - current;
            if (Math.Abs(diff) < 0.004f) return target;
            float factor = 1f - (float)Math.Exp(-speed * dt);
            return current + diff * factor;
        }

        private float GetHover(string id)
        {
            float v;
            return _hoverProgress.TryGetValue(id, out v) ? v : 0f;
        }

        private float GetPress(string id)
        {
            float v;
            return _pressProgress.TryGetValue(id, out v) ? v : 0f;
        }

        private void EnsureAnimRunning()
        {
            if (!_animTimer.Enabled)
            {
                _lastTickMs = Environment.TickCount & 0x7FFFFFFF;
                _animTimer.Start();
            }
        }

        private void TriggerRipple(string id, Point pt)
        {
            Rectangle clip = Rectangle.Empty;
            int radius = S(20);
            bool light = false;

            if (id == "primary")
            {
                clip = _rectPrimaryBtn;
                radius = clip.Height / 2;
                light = true;
            }
            else if (id == "secondary")
            {
                clip = _rectSecondaryBtn;
                radius = clip.Height / 2;
            }
            else if (id == "browse")
            {
                clip = _rectBrowseBtn;
                radius = clip.Height / 2;
            }
            else if (id == "row_desktop")
            {
                clip = _rectRowDesktop;
                radius = S(10);
            }
            else if (id == "row_start")
            {
                clip = _rectRowStartMenu;
                radius = S(10);
            }
            else if (id == "min")
            {
                clip = _rectMinBtn;
                radius = clip.Height / 2;
            }
            else if (id == "close")
            {
                clip = _rectCloseBtn;
                radius = clip.Height / 2;
            }

            if (clip.IsEmpty) return;
            RippleEffect r = new RippleEffect();
            r.Origin = pt;
            r.ClipBounds = clip;
            r.CornerRadius = radius;
            r.Progress = 0.05f;
            r.Alpha = 1.0f;
            r.IsHolding = true;
            r.LightRipple = light;
            _ripples.Add(r);
            EnsureAnimRunning();
        }

        private void ReleaseRipples()
        {
            for (int i = 0; i < _ripples.Count; i++)
            {
                _ripples[i].IsHolding = false;
            }
        }

        private void OnAnimTick(object sender, EventArgs e)
        {
            long now = Environment.TickCount & 0x7FFFFFFF;
            float dt = Math.Max(0.005f, Math.Min(0.05f, (now - _lastTickMs) / 1000.0f));
            _lastTickMs = now;

            bool needsMore = false;
            string[] ids = new string[] { "close", "min", "primary", "secondary", "browse", "row_desktop", "row_start" };
            for (int i = 0; i < ids.Length; i++)
            {
                string id = ids[i];
                float hTarget = (_hoveredId == id) ? 1f : 0f;
                float hCur = GetHover(id);
                float hNext = Approach(hCur, hTarget, 18f, dt);
                _hoverProgress[id] = hNext;
                if (hNext != hTarget) needsMore = true;

                float pTarget = (_pressedId == id) ? 1f : 0f;
                float pCur = GetPress(id);
                float pNext = Approach(pCur, pTarget, 26f, dt);
                _pressProgress[id] = pNext;
                if (pNext != pTarget) needsMore = true;
            }

            float deskTarget = _optDesktopShortcut ? 1f : 0f;
            _desktopSwitchAnim = Approach(_desktopSwitchAnim, deskTarget, 17f, dt);
            if (_desktopSwitchAnim != deskTarget) needsMore = true;

            float startTarget = _optStartMenuShortcut ? 1f : 0f;
            _startSwitchAnim = Approach(_startSwitchAnim, startTarget, 17f, dt);
            if (_startSwitchAnim != startTarget) needsMore = true;

            float focusTarget = _pathTextBox.Focused ? 1f : 0f;
            _inputFocusAnim = Approach(_inputFocusAnim, focusTarget, 20f, dt);
            if (_inputFocusAnim != focusTarget) needsMore = true;

            if (_stageTransitionAnim < 1.0f)
            {
                _stageTransitionAnim = Math.Min(1.0f, _stageTransitionAnim + dt / 0.22f);
                needsMore = true;
            }

            if (_stage == SetupStage.Working)
            {
                _visualProgress = Approach(_visualProgress, _progressPercent, 14f, dt);
                _progressShimmerPhase = (_progressShimmerPhase + dt * 1.15f) % 1.6f;
                needsMore = true;
            }

            if (_stage == SetupStage.Completed && _completeCheckAnim < 1.0f)
            {
                _completeCheckAnim = Math.Min(1.0f, _completeCheckAnim + dt / 0.36f);
                needsMore = true;
            }

            for (int i = _ripples.Count - 1; i >= 0; i--)
            {
                RippleEffect rp = _ripples[i];
                rp.Progress = Math.Min(1.0f, rp.Progress + dt * 3.1f);
                if (!rp.IsHolding)
                {
                    rp.Alpha -= dt * 3.0f;
                }
                if (rp.Alpha <= 0.01f)
                {
                    _ripples.RemoveAt(i);
                }
                else
                {
                    needsMore = true;
                }
            }

            Invalidate();
            if (!needsMore)
            {
                _animTimer.Stop();
            }
        }

        private Rectangle GetActionRect(string id)
        {
            if (id == "primary") return _rectPrimaryBtn;
            if (id == "secondary") return _rectSecondaryBtn;
            if (id == "browse") return _rectBrowseBtn;
            if (id == "row_desktop") return _rectRowDesktop;
            if (id == "row_start") return _rectRowStartMenu;
            if (id == "min") return _rectMinBtn;
            if (id == "close") return _rectCloseBtn;
            return Rectangle.Empty;
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.TextRenderingHint = TextRenderingHint.ClearTypeGridFit;

            int w = ClientSize.Width;
            int h = ClientSize.Height;

            // 1. M3 Tinted Window Surface Base
            using (SolidBrush bgBrush = new SolidBrush(_theme.WindowSurface))
            {
                g.FillRectangle(bgBrush, 0, 0, w, h);
            }

            DrawWindowControls(g, w);
            DrawBrandHeader(g);

            _rectPrimaryBtn = Rectangle.Empty;
            _rectSecondaryBtn = Rectangle.Empty;
            _rectBrowseBtn = Rectangle.Empty;
            _rectRowDesktop = Rectangle.Empty;
            _rectRowStartMenu = Rectangle.Empty;

            float stageEase = EaseOutCubic(_stageTransitionAnim);
            int slideY = (int)Math.Round((1.0f - stageEase) * Sf(10f));

            // 2. Elevated M3 White Surface Card (24dp corners, fixed position for rock-solid container geometry)
            Rectangle cardRect = new Rectangle(S(24), S(92), w - S(48), S(206));
            Color cardFill = (_stage == SetupStage.Error) ? _theme.ErrorContainer : _theme.CardSurface;
            using (GraphicsPath cp = CreateRoundRect(cardRect, S(24)))
            using (SolidBrush cb = new SolidBrush(cardFill))
            {
                g.FillPath(cb, cp);
                if (_theme.IsDark && _stage != SetupStage.Error)
                {
                    using (Pen darkCardEdge = new Pen(_theme.OutlineVariant, 1f))
                    {
                        g.DrawPath(darkCardEdge, cp);
                    }
                }
            }

            Rectangle animatedContentCard = new Rectangle(cardRect.X, cardRect.Y + slideY, cardRect.Width, cardRect.Height);
            if (_stage == SetupStage.Ready)
            {
                if (_isUninstallMode)
                {
                    DrawUninstallReadyBody(g, w, animatedContentCard);
                }
                else
                {
                    DrawInstallReadyBody(g, w, animatedContentCard);
                }
            }
            else if (_stage == SetupStage.Working)
            {
                DrawWorkingProgressBody(g, animatedContentCard);
            }
            else if (_stage == SetupStage.Completed)
            {
                DrawCompletedBody(g, w, animatedContentCard);
            }
            else if (_stage == SetupStage.Error)
            {
                DrawErrorBody(g, w, animatedContentCard);
            }

            if (!_hasDwmRound)
            {
                using (GraphicsPath borderPath = CreateRoundRect(new Rectangle(0, 0, w - 1, h - 1), S(12)))
                using (Pen borderPen = new Pen(_theme.WindowBorder, 1f))
                {
                    g.DrawPath(borderPen, borderPath);
                }
            }
        }

        private void DrawRipplesForControl(Graphics g, Rectangle bounds, int radius, bool lightRipple)
        {
            for (int i = 0; i < _ripples.Count; i++)
            {
                RippleEffect rp = _ripples[i];
                if (rp.ClipBounds != bounds) continue;
                float maxDist = (float)Math.Sqrt(bounds.Width * bounds.Width + bounds.Height * bounds.Height);
                float curR = Math.Max(Sf(8f), maxDist * EaseOutCubic(rp.Progress));
                Color baseCol = lightRipple ? _theme.RippleLight : _theme.RippleDark;
                int a = Math.Max(0, Math.Min(255, (int)Math.Round(baseCol.A * rp.Alpha)));
                if (a <= 1) continue;

                GraphicsState saved = g.Save();
                using (GraphicsPath clipPath = CreateRoundRect(bounds, radius))
                using (SolidBrush rb = new SolidBrush(Color.FromArgb(a, baseCol.R, baseCol.G, baseCol.B)))
                {
                    g.SetClip(clipPath, CombineMode.Intersect);
                    g.FillEllipse(rb, rp.Origin.X - curR, rp.Origin.Y - curR, curR * 2f, curR * 2f);
                }
                g.Restore(saved);
            }
        }

        private Rectangle ApplyPressScale(Rectangle rect, float press)
        {
            if (press <= 0.005f) return rect;
            float dx = rect.Width * 0.015f * press;
            float dy = rect.Height * 0.025f * press;
            return new Rectangle(
                (int)Math.Round(rect.X + dx),
                (int)Math.Round(rect.Y + dy),
                Math.Max(4, (int)Math.Round(rect.Width - dx * 2f)),
                Math.Max(4, (int)Math.Round(rect.Height - dy * 2f)));
        }

        private void DrawWindowControls(Graphics g, int w)
        {
            int btnSz = S(32);
            _rectCloseBtn = new Rectangle(w - S(44), S(14), btnSz, btnSz);
            _rectMinBtn = new Rectangle(w - S(80), S(14), btnSz, btnSz);

            bool closeEnabled = _stage != SetupStage.Working;
            float closeHover = closeEnabled ? GetHover("close") : 0f;
            float minHover = GetHover("min");

            if (closeHover > 0.01f)
            {
                int a = (int)Math.Round(_theme.ErrorContainer.A * closeHover);
                using (SolidBrush b = new SolidBrush(Color.FromArgb(a, _theme.ErrorContainer)))
                {
                    g.FillEllipse(b, _rectCloseBtn);
                }
            }
            if (minHover > 0.01f)
            {
                int a = (int)Math.Round(255f * minHover);
                using (SolidBrush b = new SolidBrush(Color.FromArgb(a, _theme.SurfaceContainerHigh)))
                {
                    g.FillEllipse(b, _rectMinBtn);
                }
            }

            DrawRipplesForControl(g, _rectMinBtn, _rectMinBtn.Height / 2, false);
            DrawRipplesForControl(g, _rectCloseBtn, _rectCloseBtn.Height / 2, false);

            float strokeW = Math.Max(1.4f, 1.5f * _dpiScale);
            using (Pen minPen = new Pen(_theme.OnSurfaceVariant, strokeW))
            {
                minPen.StartCap = LineCap.Round;
                minPen.EndCap = LineCap.Round;
                int my = _rectMinBtn.Y + _rectMinBtn.Height / 2;
                g.DrawLine(minPen, _rectMinBtn.X + S(10), my, _rectMinBtn.Right - S(10), my);
            }

            Color closeCol = !closeEnabled
                ? _theme.OutlineVariant
                : LerpColor(_theme.OnSurfaceVariant, _theme.Error, closeHover);
            using (Pen closePen = new Pen(closeCol, strokeW))
            {
                closePen.StartCap = LineCap.Round;
                closePen.EndCap = LineCap.Round;
                int cx1 = _rectCloseBtn.X + S(11);
                int cy1 = _rectCloseBtn.Y + S(11);
                int cx2 = _rectCloseBtn.Right - S(11);
                int cy2 = _rectCloseBtn.Bottom - S(11);
                g.DrawLine(closePen, cx1, cy1, cx2, cy2);
                g.DrawLine(closePen, cx2, cy1, cx1, cy2);
            }
        }

        private void DrawBrandHeader(Graphics g)
        {
            int logoSize = S(46);
            int logoX = S(26);
            int logoY = S(24);
            if (_logoImage != null)
            {
                g.DrawImage(_logoImage, new Rectangle(logoX, logoY, logoSize, logoSize));
            }
            else
            {
                using (GraphicsPath p = CreateRoundRect(new Rectangle(logoX, logoY, logoSize, logoSize), S(14)))
                using (SolidBrush b = new SolidBrush(_theme.PrimaryContainer))
                {
                    g.FillPath(b, p);
                }
            }

            int textLeft = logoX + logoSize + S(14);
            using (Font titleFont = new Font("Segoe UI", Sf(23f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush titleBrush = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString("PhonAcq", titleFont, titleBrush, textLeft - S(2), logoY - S(2));
                SizeF titleSz = g.MeasureString("PhonAcq", titleFont);

                string chipText = _isUninstallMode ? "卸载程序" : ("v" + _appVersion);
                using (Font chipFont = new Font("Microsoft YaHei UI", Sf(11.5f), FontStyle.Bold, GraphicsUnit.Pixel))
                using (SolidBrush chipFill = new SolidBrush(_theme.SecondaryContainer))
                using (SolidBrush chipFg = new SolidBrush(_theme.OnSecondaryContainer))
                {
                    SizeF cSz = g.MeasureString(chipText, chipFont);
                    int chipH = S(22);
                    int chipW = (int)Math.Ceiling(cSz.Width) + S(14);
                    Rectangle chipRect = new Rectangle(
                        textLeft + (int)Math.Ceiling(titleSz.Width) + S(4),
                        logoY + S(5),
                        chipW,
                        chipH);
                    using (GraphicsPath cp = CreateRoundRect(chipRect, chipH / 2))
                    {
                        g.FillPath(chipFill, cp);
                    }
                    StringFormat csf = new StringFormat();
                    csf.Alignment = StringAlignment.Center;
                    csf.LineAlignment = StringAlignment.Center;
                    g.DrawString(chipText, chipFont, chipFg, chipRect, csf);
                }
            }

            using (Font subFont = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush subBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                string subtitle = _isUninstallMode
                    ? "从本机移除 PhonAcq 桌面端程序"
                    : "田野语音与方言字表采录系统";
                g.DrawString(subtitle, subFont, subBrush, textLeft, logoY + S(29));
            }
        }

        private void DrawInstallReadyBody(Graphics g, int w, Rectangle cardRect)
        {
            // 1. Install Location Field (M3 Filled/Outlined Container)
            using (Font lblFont = new Font("Microsoft YaHei UI", Sf(11.8f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush lblBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                g.DrawString("安装位置", lblFont, lblBrush, S(44), cardRect.Y + S(19));
            }

            Rectangle inputRect = new Rectangle(S(44), cardRect.Y + S(46), S(342), S(40));
            Color inputBorder = LerpColor(_theme.OutlineVariant, _theme.Primary, _inputFocusAnim);
            float inputBorderW = (1.0f + 0.9f * _inputFocusAnim) * _dpiScale;
            using (GraphicsPath ip = CreateRoundRect(inputRect, S(12)))
            using (SolidBrush ib = new SolidBrush(_theme.InputSurface))
            using (Pen iPen = new Pen(inputBorder, inputBorderW))
            {
                g.FillPath(ib, ip);
                g.DrawPath(iPen, ip);
            }

            _rectBrowseBtn = new Rectangle(inputRect.Right + S(10), cardRect.Y + S(46), S(76), S(40));
            DrawTonalButton(g, _rectBrowseBtn, "更改...", "browse");

            // Subtle tonal divider inside card
            using (Pen divPen = new Pen(_theme.OutlineVariant, 1f))
            {
                g.DrawLine(divPen, S(44), cardRect.Y + S(104), cardRect.Right - S(20), cardRect.Y + S(104));
            }

            // 2. Two Interactive M3 Switch List Rows
            _rectRowDesktop = new Rectangle(S(34), cardRect.Y + S(114), cardRect.Width - S(20), S(38));
            DrawMaterialSwitchRow(g, _rectRowDesktop, "创建桌面快捷方式", _optDesktopShortcut, _desktopSwitchAnim, "row_desktop");

            _rectRowStartMenu = new Rectangle(S(34), cardRect.Y + S(156), cardRect.Width - S(20), S(38));
            DrawMaterialSwitchRow(g, _rectRowStartMenu, "添加到开始菜单", _optStartMenuShortcut, _startSwitchAnim, "row_start");

            // 3. Bottom M3 Dialog Footer Row
            int btnY = S(316);
            int btnH = S(40);
            int primaryW = S(116);
            int cancelW = S(76);
            int rightEdge = w - S(24);

            _rectPrimaryBtn = new Rectangle(rightEdge - primaryW, btnY, primaryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - cancelW, btnY, cancelW, btnH);

            if (_isOverwriteUpgrade)
            {
                int dotSz = S(18);
                Rectangle dotRect = new Rectangle(S(26), btnY + (btnH - dotSz) / 2, dotSz, dotSz);
                using (SolidBrush db = new SolidBrush(_theme.SecondaryContainer))
                using (Pen ckPen = new Pen(_theme.OnSecondaryContainer, Math.Max(1.5f, 1.6f * _dpiScale)))
                {
                    g.FillEllipse(db, dotRect);
                    ckPen.StartCap = LineCap.Round;
                    ckPen.EndCap = LineCap.Round;
                    ckPen.LineJoin = LineJoin.Round;
                    g.DrawLines(ckPen, new Point[]
                    {
                        new Point(dotRect.X + S(4), dotRect.Y + S(9)),
                        new Point(dotRect.X + S(8), dotRect.Y + S(13)),
                        new Point(dotRect.X + S(14), dotRect.Y + S(5))
                    });
                }

                using (Font noteFont = new Font("Microsoft YaHei UI", Sf(11.5f), FontStyle.Regular, GraphicsUnit.Pixel))
                using (SolidBrush noteBrush = new SolidBrush(_theme.OnSurfaceVariant))
                {
                    string verHint = !string.IsNullOrEmpty(_existingVersion)
                        ? String.Format("已安装 v{0} · 覆盖保留本地数据", _existingVersion)
                        : "已安装旧版 · 覆盖保留本地数据";
                    RectangleF nr = new RectangleF(dotRect.Right + S(8), btnY, _rectSecondaryBtn.X - dotRect.Right - S(14), btnH);
                    StringFormat nsf = new StringFormat();
                    nsf.LineAlignment = StringAlignment.Center;
                    nsf.Trimming = StringTrimming.EllipsisCharacter;
                    nsf.FormatFlags = StringFormatFlags.NoWrap;
                    g.DrawString(verHint, noteFont, noteBrush, nr, nsf);
                }
            }

            DrawTextButton(g, _rectSecondaryBtn, "取消", "secondary");
            string ctaLabel = _isOverwriteUpgrade ? "覆盖安装" : "开始安装";
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, ctaLabel, "primary", false);
        }

        private void DrawUninstallReadyBody(Graphics g, int w, Rectangle cardRect)
        {
            using (Font hFont = new Font("Microsoft YaHei UI", Sf(14.5f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush hb = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString("确认从本机卸载 PhonAcq？", hFont, hb, S(46), cardRect.Y + S(26));
            }

            using (Font bFont = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush bb = new SolidBrush(_theme.OnSurfaceVariant))
            {
                RectangleF bodyLayout = new RectangleF(S(46), cardRect.Y + S(58), cardRect.Width - S(44), S(60));
                g.DrawString(
                    "将移除 PhonAcq 程序文件与桌面快捷方式。\n您在本地保存的所有录音 (.wav) 与调查字表工程将完整保留。",
                    bFont, bb, bodyLayout);
            }

            // Subtle tonal pill showing target uninstall directory
            Rectangle pathPill = new Rectangle(S(46), cardRect.Y + S(136), cardRect.Width - S(44), S(34));
            using (GraphicsPath pp = CreateRoundRect(pathPill, S(10)))
            using (SolidBrush pbFill = new SolidBrush(_theme.SurfaceContainer))
            using (Font pFont = new Font("Microsoft YaHei UI", Sf(11.3f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush pb = new SolidBrush(_theme.OnSurfaceVariant))
            {
                g.FillPath(pbFill, pp);
                StringFormat psf = new StringFormat();
                psf.LineAlignment = StringAlignment.Center;
                psf.Trimming = StringTrimming.EllipsisPath;
                psf.FormatFlags = StringFormatFlags.NoWrap;
                RectangleF pr = new RectangleF(pathPill.X + S(12), pathPill.Y, pathPill.Width - S(24), pathPill.Height);
                g.DrawString("安装目录：" + _installDir, pFont, pb, pr, psf);
            }

            int btnY = S(316);
            int btnH = S(40);
            int primaryW = S(104);
            int cancelW = S(76);
            int rightEdge = w - S(24);

            _rectPrimaryBtn = new Rectangle(rightEdge - primaryW, btnY, primaryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - cancelW, btnY, cancelW, btnH);

            DrawTextButton(g, _rectSecondaryBtn, "取消", "secondary");
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, "卸载", "primary", true);
        }

        private void DrawWorkingProgressBody(Graphics g, Rectangle cardRect)
        {
            string workTitle = _isUninstallMode ? "正在卸载 PhonAcq" : "正在安装 PhonAcq";
            using (Font tFont = new Font("Microsoft YaHei UI", Sf(14.5f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(_theme.OnSurface))
            {
                g.DrawString(workTitle, tFont, tb, S(46), cardRect.Y + S(44));
            }

            int shownPct = Math.Max(0, Math.Min(100, (int)Math.Round(_visualProgress)));
            using (Font pctFont = new Font("Segoe UI Semibold", Sf(15f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush pctBrush = new SolidBrush(_theme.Primary))
            {
                StringFormat rsf = new StringFormat();
                rsf.Alignment = StringAlignment.Far;
                g.DrawString(
                    String.Format("{0}%", shownPct),
                    pctFont,
                    pctBrush,
                    new RectangleF(S(46), cardRect.Y + S(43), cardRect.Width - S(44), S(28)),
                    rsf);
            }

            // Smooth M3 LinearProgressIndicator with subtle moving highlight shimmer
            int trackX = S(46);
            int trackY = cardRect.Y + S(88);
            int trackW = cardRect.Width - S(44);
            int trackH = S(10);
            Rectangle trackRect = new Rectangle(trackX, trackY, trackW, trackH);
            using (GraphicsPath tp = CreateRoundRect(trackRect, trackH / 2))
            using (SolidBrush trkBrush = new SolidBrush(_theme.PrimaryContainer))
            {
                g.FillPath(trkBrush, tp);
            }

            if (_visualProgress > 0.5f)
            {
                int fillW = Math.Max(trackH, (int)Math.Round((trackW * Math.Min(100f, _visualProgress)) / 100.0f));
                Rectangle fillRect = new Rectangle(trackX, trackY, Math.Min(trackW, fillW), trackH);
                using (GraphicsPath fp = CreateRoundRect(fillRect, trackH / 2))
                using (SolidBrush fillBrush = new SolidBrush(_theme.Primary))
                {
                    g.FillPath(fillBrush, fp);

                    // Moving subtle highlight sweep across active progress fill
                    GraphicsState saved = g.Save();
                    g.SetClip(fp, CombineMode.Intersect);
                    float sweepX = fillRect.X - S(40) + (fillRect.Width + S(80)) * (_progressShimmerPhase / 1.6f);
                    RectangleF sweepRect = new RectangleF(sweepX, fillRect.Y, S(56), fillRect.Height);
                    using (LinearGradientBrush shimmer = new LinearGradientBrush(
                        sweepRect,
                        Color.FromArgb(0, 255, 255, 255),
                        Color.FromArgb(_theme.IsDark ? 55 : 75, 255, 255, 255),
                        0f))
                    {
                        ColorBlend blend = new ColorBlend(3);
                        blend.Colors = new Color[]
                        {
                            Color.FromArgb(0, 255, 255, 255),
                            Color.FromArgb(_theme.IsDark ? 55 : 75, 255, 255, 255),
                            Color.FromArgb(0, 255, 255, 255)
                        };
                        blend.Positions = new float[] { 0f, 0.5f, 1f };
                        shimmer.InterpolationColors = blend;
                        g.FillRectangle(shimmer, sweepRect);
                    }
                    g.Restore(saved);
                }
            }

            using (Font stFont = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush stBrush = new SolidBrush(_theme.OnSurfaceVariant))
            {
                g.DrawString(_statusMessage, stFont, stBrush, S(46), cardRect.Y + S(122));
            }
        }

        private void DrawCompletedBody(Graphics g, int w, Rectangle cardRect)
        {
            float checkEase = EaseOutCubic(_completeCheckAnim);
            int cSize = S(52);
            Rectangle circleRect = new Rectangle(S(46), cardRect.Y + S(52), cSize, cSize);
            using (SolidBrush fill = new SolidBrush(_theme.SecondaryContainer))
            {
                g.FillEllipse(fill, circleRect);
            }

            if (checkEase > 0.02f)
            {
                PointF p1 = new PointF(circleRect.X + Sf(15f), circleRect.Y + Sf(27f));
                PointF p2 = new PointF(circleRect.X + Sf(22.5f), circleRect.Y + Sf(34.5f));
                PointF p3 = new PointF(circleRect.X + Sf(37.5f), circleRect.Y + Sf(18f));
                using (Pen checkPen = new Pen(_theme.Primary, Math.Max(2.4f, Sf(2.6f))))
                {
                    checkPen.StartCap = LineCap.Round;
                    checkPen.EndCap = LineCap.Round;
                    checkPen.LineJoin = LineJoin.Round;

                    if (checkEase <= 0.4f)
                    {
                        float t1 = checkEase / 0.4f;
                        PointF mid = new PointF(p1.X + (p2.X - p1.X) * t1, p1.Y + (p2.Y - p1.Y) * t1);
                        g.DrawLine(checkPen, p1, mid);
                    }
                    else
                    {
                        float t2 = (checkEase - 0.4f) / 0.6f;
                        PointF end = new PointF(p2.X + (p3.X - p2.X) * t2, p2.Y + (p3.Y - p2.Y) * t2);
                        g.DrawLines(checkPen, new PointF[] { p1, p2, end });
                    }
                }
            }

            int textX = circleRect.Right + S(18);
            using (Font hFont = new Font("Microsoft YaHei UI", Sf(16.5f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush hb = new SolidBrush(_theme.OnSurface))
            {
                string doneTitle = _isUninstallMode ? "PhonAcq 已卸载" : "安装完成";
                g.DrawString(doneTitle, hFont, hb, textX, cardRect.Y + S(50));
            }

            using (Font dFont = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush db = new SolidBrush(_theme.OnSurfaceVariant))
            {
                string doneDesc = _isUninstallMode
                    ? "程序文件已移除，本地录音与调查字表数据均已保留。"
                    : "PhonAcq 已准备就绪，现在可以开始语音采录与声学分析。";
                RectangleF dr = new RectangleF(textX, cardRect.Y + S(82), cardRect.Right - textX - S(22), S(56));
                g.DrawString(doneDesc, dFont, db, dr);
            }

            int btnY = S(316);
            int btnH = S(40);
            int rightEdge = w - S(24);

            if (_isUninstallMode)
            {
                int btnW = S(104);
                _rectPrimaryBtn = new Rectangle(rightEdge - btnW, btnY, btnW, btnH);
                DrawPrimaryFilledButton(g, _rectPrimaryBtn, "完成", "primary", false);
            }
            else
            {
                int openW = S(136);
                int closeW = S(76);
                _rectPrimaryBtn = new Rectangle(rightEdge - openW, btnY, openW, btnH);
                _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - closeW, btnY, closeW, btnH);

                DrawTextButton(g, _rectSecondaryBtn, "关闭", "secondary");
                DrawPrimaryFilledButton(g, _rectPrimaryBtn, "打开 PhonAcq", "primary", false);
            }
        }

        private void DrawErrorBody(Graphics g, int w, Rectangle cardRect)
        {
            using (Font hFont = new Font("Microsoft YaHei UI", Sf(14.5f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush hb = new SolidBrush(_theme.OnErrorContainer))
            {
                g.DrawString("未能完成操作", hFont, hb, S(46), cardRect.Y + S(28));
            }

            using (Font f = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(_theme.OnErrorContainer))
            {
                RectangleF tr = new RectangleF(S(46), cardRect.Y + S(62), cardRect.Width - S(44), S(120));
                g.DrawString(_errorMessage, f, tb, tr);
            }

            int btnY = S(316);
            int btnH = S(40);
            int retryW = S(96);
            int closeW = S(76);
            int rightEdge = w - S(24);

            _rectPrimaryBtn = new Rectangle(rightEdge - retryW, btnY, retryW, btnH);
            _rectSecondaryBtn = new Rectangle(_rectPrimaryBtn.X - S(10) - closeW, btnY, closeW, btnH);

            DrawTextButton(g, _rectSecondaryBtn, "关闭", "secondary");
            DrawPrimaryFilledButton(g, _rectPrimaryBtn, "重试", "primary", false);
        }

        private void DrawPrimaryFilledButton(Graphics g, Rectangle rawRect, string text, string id, bool danger)
        {
            float hover = GetHover(id);
            float press = GetPress(id);
            Rectangle r = ApplyPressScale(rawRect, press);

            Color baseFill = danger ? _theme.Error : _theme.Primary;
            Color hoverFill = danger ? _theme.ErrorHover : _theme.PrimaryHover;
            Color fill = LerpColor(baseFill, hoverFill, hover);
            Color fg = danger ? _theme.OnError : _theme.OnPrimary;

            using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
            using (SolidBrush b = new SolidBrush(fill))
            using (Font f = new Font("Microsoft YaHei UI", Sf(13.2f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(fg))
            {
                g.FillPath(b, p);
                DrawRipplesForControl(g, rawRect, rawRect.Height / 2, true);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawTonalButton(Graphics g, Rectangle rawRect, string text, string id)
        {
            float hover = GetHover(id);
            float press = GetPress(id);
            Rectangle r = ApplyPressScale(rawRect, press);

            Color fill = LerpColor(_theme.SecondaryContainer, _theme.SecondaryContainerHover, hover);
            using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
            using (SolidBrush b = new SolidBrush(fill))
            using (Font f = new Font("Microsoft YaHei UI", Sf(12.2f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(_theme.OnSecondaryContainer))
            {
                g.FillPath(b, p);
                DrawRipplesForControl(g, rawRect, rawRect.Height / 2, false);
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawTextButton(Graphics g, Rectangle rawRect, string text, string id)
        {
            float hover = GetHover(id);
            float press = GetPress(id);
            Rectangle r = ApplyPressScale(rawRect, press);

            if (hover > 0.01f)
            {
                int a = (int)Math.Round(255f * hover);
                using (GraphicsPath p = CreateRoundRect(r, r.Height / 2))
                using (SolidBrush b = new SolidBrush(Color.FromArgb(a, _theme.SurfaceContainerHigh)))
                {
                    g.FillPath(b, p);
                }
            }
            DrawRipplesForControl(g, rawRect, rawRect.Height / 2, false);

            Color fg = LerpColor(_theme.OnSurfaceVariant, _theme.Primary, hover * 0.75f);
            using (Font f = new Font("Microsoft YaHei UI", Sf(12.8f), FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(fg))
            {
                StringFormat sf = new StringFormat();
                sf.Alignment = StringAlignment.Center;
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(text, f, tb, r, sf);
            }
        }

        private void DrawMaterialSwitchRow(
            Graphics g,
            Rectangle rowRect,
            string label,
            bool isChecked,
            float switchAnim,
            string id)
        {
            float hover = GetHover(id);
            float press = GetPress(id);

            // Subtle M3 hover state layer across the row
            if (hover > 0.01f)
            {
                int a = (int)Math.Round(_theme.StateLayerHover.A * hover * 1.4f);
                using (GraphicsPath rp = CreateRoundRect(rowRect, S(10)))
                using (SolidBrush rb = new SolidBrush(Color.FromArgb(Math.Min(255, a), _theme.StateLayerHover.R, _theme.StateLayerHover.G, _theme.StateLayerHover.B)))
                {
                    g.FillPath(rb, rp);
                }
            }
            DrawRipplesForControl(g, rowRect, S(10), false);

            // Label on left
            using (Font f = new Font("Microsoft YaHei UI", Sf(12.5f), FontStyle.Regular, GraphicsUnit.Pixel))
            using (SolidBrush tb = new SolidBrush(_theme.OnSurface))
            {
                StringFormat sf = new StringFormat();
                sf.LineAlignment = StringAlignment.Center;
                g.DrawString(label, f, tb, new RectangleF(rowRect.X + S(10), rowRect.Y, rowRect.Width - S(80), rowRect.Height), sf);
            }

            // Google Material 3 Expressive Switch on right
            int swW = S(46);
            int swH = S(26);
            int swX = rowRect.Right - S(10) - swW;
            int swY = rowRect.Y + (rowRect.Height - swH) / 2;
            Rectangle trackRect = new Rectangle(swX, swY, swW, swH);

            float t = Math.Max(0f, Math.Min(1f, switchAnim));
            Color trackFill = LerpColor(_theme.SurfaceContainerHighest, _theme.Primary, t);
            using (GraphicsPath tp = CreateRoundRect(trackRect, swH / 2))
            using (SolidBrush trackBrush = new SolidBrush(trackFill))
            {
                g.FillPath(trackBrush, tp);
                if (t < 0.99f)
                {
                    int borderAlpha = (int)Math.Round(255f * (1f - t));
                    Color borderCol = LerpColor(_theme.Outline, _theme.OnSurface, hover * 0.35f);
                    using (Pen trackBorder = new Pen(Color.FromArgb(borderAlpha, borderCol.R, borderCol.G, borderCol.B), Math.Max(1.4f, Sf(1.6f))))
                    {
                        g.DrawPath(trackBorder, tp);
                    }
                }
            }

            // Thumb morphs from 14px unchecked -> 20px checked, slightly expanding on press
            float baseThumb = Sf(14f) + Sf(6f) * t;
            float thumbSz = baseThumb + Sf(2.2f) * press;
            float leftCx = swX + Sf(13f);
            float rightCx = swX + swW - Sf(13f);
            float thumbCx = leftCx + (rightCx - leftCx) * t;
            float thumbCy = swY + swH / 2.0f;

            RectangleF thumbRect = new RectangleF(thumbCx - thumbSz / 2f, thumbCy - thumbSz / 2f, thumbSz, thumbSz);
            Color thumbCol = LerpColor(_theme.Outline, _theme.OnPrimary, t);
            if (t < 0.5f && hover > 0.01f)
            {
                thumbCol = LerpColor(thumbCol, _theme.OnSurfaceVariant, hover * 0.5f);
            }
            using (SolidBrush thumbBrush = new SolidBrush(thumbCol))
            {
                g.FillEllipse(thumbBrush, thumbRect);
            }

            // Draw M3 checkmark inside thumb when switched ON
            if (t > 0.35f)
            {
                float iconAlpha = Math.Max(0f, Math.Min(1f, (t - 0.35f) / 0.65f));
                int a = (int)Math.Round(255f * iconAlpha);
                using (Pen ckPen = new Pen(Color.FromArgb(a, _theme.Primary.R, _theme.Primary.G, _theme.Primary.B), Math.Max(1.5f, Sf(1.7f))))
                {
                    ckPen.StartCap = LineCap.Round;
                    ckPen.EndCap = LineCap.Round;
                    ckPen.LineJoin = LineJoin.Round;
                    float sScale = 0.7f + 0.3f * iconAlpha;
                    PointF[] ckPts = new PointF[]
                    {
                        new PointF(thumbCx - Sf(3.6f) * sScale, thumbCy + Sf(0.2f) * sScale),
                        new PointF(thumbCx - Sf(0.9f) * sScale, thumbCy + Sf(3.0f) * sScale),
                        new PointF(thumbCx + Sf(3.8f) * sScale, thumbCy - Sf(2.6f) * sScale)
                    };
                    g.DrawLines(ckPen, ckPts);
                }
            }
        }

        private string HitTestId(Point pt)
        {
            if (_rectCloseBtn.Contains(pt) && _stage != SetupStage.Working) return "close";
            if (_rectMinBtn.Contains(pt)) return "min";
            if (_rectPrimaryBtn.Contains(pt)) return "primary";
            if (_rectSecondaryBtn.Contains(pt)) return "secondary";
            if (_rectBrowseBtn.Contains(pt)) return "browse";
            if (_rectRowDesktop.Contains(pt)) return "row_desktop";
            if (_rectRowStartMenu.Contains(pt)) return "row_start";
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            string hit = HitTestId(e.Location);
            if (hit != _hoveredId)
            {
                _hoveredId = hit;
                Cursor = hit != null ? Cursors.Hand : Cursors.Default;
                EnsureAnimRunning();
                Invalidate();
            }
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            if (_hoveredId != null || _pressedId != null)
            {
                _hoveredId = null;
                _pressedId = null;
                ReleaseRipples();
                Cursor = Cursors.Default;
                EnsureAnimRunning();
                Invalidate();
            }
        }

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left) return;

            string hit = HitTestId(e.Location);
            if (hit == null && e.Y <= S(88))
            {
                NativeMethods.ReleaseCapture();
                NativeMethods.SendMessage(Handle, NativeMethods.WM_NCLBUTTONDOWN, NativeMethods.HTCAPTION, 0);
                return;
            }

            if (hit != null)
            {
                _pressedId = hit;
                TriggerRipple(hit, e.Location);
                EnsureAnimRunning();
                Invalidate();
            }
        }

        protected override void OnMouseUp(MouseEventArgs e)
        {
            base.OnMouseUp(e);
            if (e.Button != MouseButtons.Left) return;

            string wasPressed = _pressedId;
            _pressedId = null;
            ReleaseRipples();
            if (wasPressed != null)
            {
                EnsureAnimRunning();
                Invalidate();
            }

            string hit = HitTestId(e.Location);
            if (hit == null || hit != wasPressed) return;

            if (hit == "min")
            {
                WindowState = FormWindowState.Minimized;
            }
            else if (hit == "close")
            {
                if (_stage != SetupStage.Working) Close();
            }
            else if (hit == "browse")
            {
                string picked = ModernFolderBrowser.PickFolder(Handle, _installDir, "选择 PhonAcq 安装位置");
                if (!string.IsNullOrEmpty(picked))
                {
                    if (!picked.EndsWith("PhonAcq", StringComparison.OrdinalIgnoreCase))
                    {
                        picked = Path.Combine(picked, "PhonAcq");
                    }
                    _installDir = picked;
                    _pathTextBox.Text = _installDir;
                    _pathTextBox.SelectionLength = 0;
                    _pathTextBox.SelectionStart = _pathTextBox.Text.Length;
                    Invalidate();
                }
            }
            else if (hit == "row_desktop")
            {
                _optDesktopShortcut = !_optDesktopShortcut;
                EnsureAnimRunning();
                Invalidate();
            }
            else if (hit == "row_start")
            {
                _optStartMenuShortcut = !_optStartMenuShortcut;
                EnsureAnimRunning();
                Invalidate();
            }
            else if (hit == "primary")
            {
                HandlePrimaryAction();
            }
            else if (hit == "secondary")
            {
                Close();
            }
        }

        private void HandlePrimaryAction()
        {
            if (_stage == SetupStage.Completed)
            {
                if (!_isUninstallMode)
                {
                    LaunchInstalledApp();
                }
                Close();
                return;
            }

            if (_stage != SetupStage.Ready && _stage != SetupStage.Error) return;

            if (!_isUninstallMode && _payload == null)
            {
                _stage = SetupStage.Error;
                _errorMessage = "安装包数据不完整，请重新下载 PhonAcq 安装程序。";
                _stageTransitionAnim = 0.0f;
                LayoutPathTextBox();
                EnsureAnimRunning();
                Invalidate();
                return;
            }

            _stage = SetupStage.Working;
            _progressPercent = 2;
            _visualProgress = 2f;
            _statusMessage = _isUninstallMode ? "正在准备卸载..." : "正在准备安装...";
            _stageTransitionAnim = 0.0f;
            LayoutPathTextBox();
            EnsureAnimRunning();
            Invalidate();

            string targetDir = _installDir;
            bool mkDesktop = _optDesktopShortcut;
            bool mkStart = _optStartMenuShortcut;
            string selfExe = Application.ExecutablePath;

            Thread worker = new Thread(delegate()
            {
                try
                {
                    if (_isUninstallMode)
                    {
                        InstallerEngine.PerformUninstall(targetDir, selfExe, UpdateProgressSafe);
                    }
                    else
                    {
                        InstallerEngine.PerformInstall(
                            selfExe,
                            _payload,
                            targetDir,
                            mkDesktop,
                            mkStart,
                            _legacyMsiCodes,
                            UpdateProgressSafe);
                    }

                    if (!IsDisposed)
                    {
                        BeginInvoke((MethodInvoker)delegate
                        {
                            _stage = SetupStage.Completed;
                            _stageTransitionAnim = 0.0f;
                            _completeCheckAnim = 0.0f;
                            LayoutPathTextBox();
                            EnsureAnimRunning();
                            Invalidate();
                        });
                    }
                }
                catch (Exception ex)
                {
                    if (!IsDisposed)
                    {
                        BeginInvoke((MethodInvoker)delegate
                        {
                            _stage = SetupStage.Error;
                            _errorMessage = ex.Message;
                            _stageTransitionAnim = 0.0f;
                            LayoutPathTextBox();
                            EnsureAnimRunning();
                            Invalidate();
                        });
                    }
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }

        private void UpdateProgressSafe(int pct, string msg)
        {
            if (IsDisposed || !IsHandleCreated) return;
            try
            {
                BeginInvoke((MethodInvoker)delegate
                {
                    _progressPercent = Math.Max(0, Math.Min(100, pct));
                    _statusMessage = msg ?? "";
                    EnsureAnimRunning();
                    Invalidate();
                });
            }
            catch
            {
            }
        }

        private void LaunchInstalledApp()
        {
            try
            {
                string mainExe = Path.Combine(_installDir, "PhonAcq.exe");
                if (File.Exists(mainExe))
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = mainExe;
                    psi.WorkingDirectory = _installDir;
                    psi.UseShellExecute = true;
                    Process.Start(psi);
                }
            }
            catch
            {
            }
        }
    }

    internal static class Program
    {
        // Replaced at build time by Gradle packageWindowsSetup if -PappVersion is provided
        public const string BUILD_APP_VERSION = "__PHONACQ_VERSION__";

        [STAThread]
        public static int Main(string[] args)
        {
            NativeMethods.EnableHighDpi();
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            string selfExe = Application.ExecutablePath;
            string selfName = Path.GetFileName(selfExe) ?? "";
            bool isUninstall = selfName.StartsWith("Uninstall", StringComparison.OrdinalIgnoreCase);
            bool isSilent = false;
            string customDir = null;

            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    string a = args[i] ?? "";
                    if (string.Equals(a, "--uninstall", StringComparison.OrdinalIgnoreCase) ||
                        string.Equals(a, "/uninstall", StringComparison.OrdinalIgnoreCase))
                    {
                        isUninstall = true;
                    }
                    else if (string.Equals(a, "--silent", StringComparison.OrdinalIgnoreCase) ||
                             string.Equals(a, "/S", StringComparison.OrdinalIgnoreCase) ||
                             string.Equals(a, "/quiet", StringComparison.OrdinalIgnoreCase))
                    {
                        isSilent = true;
                    }
                    else if (a.StartsWith("--dir=", StringComparison.OrdinalIgnoreCase))
                    {
                        customDir = a.Substring(6).Trim('"');
                    }
                }
            }

            string ver = BUILD_APP_VERSION.StartsWith("__") ? "2.0.2" : BUILD_APP_VERSION;
            PayloadDescriptor payload = isUninstall ? null : InstallerEngine.ReadPayloadDescriptor(selfExe, ver);

            if (isSilent)
            {
                try
                {
                    string detectedDir;
                    string detectedVer;
                    List<string> msiCodes;
                    InstallerEngine.DetectExistingInstallation(out detectedDir, out detectedVer, out msiCodes);

                    if (isUninstall)
                    {
                        string target = !string.IsNullOrEmpty(detectedDir) ? detectedDir : Path.GetDirectoryName(selfExe);
                        InstallerEngine.PerformUninstall(target, selfExe, null);
                        return 0;
                    }
                    else if (payload != null)
                    {
                        string target = !string.IsNullOrEmpty(customDir)
                            ? customDir
                            : InstallerEngine.GetDefaultInstallDir(detectedDir);
                        InstallerEngine.PerformInstall(selfExe, payload, target, true, true, msiCodes, null);
                        return 0;
                    }
                    return 2;
                }
                catch
                {
                    return 1;
                }
            }

            Application.Run(new PhonAcqInstallerForm(isUninstall, ver, payload));
            return 0;
        }
    }
}
