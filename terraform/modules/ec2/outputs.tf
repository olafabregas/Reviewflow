output "instance_id" {
  value = aws_instance.app.id
}

output "public_ip" {
  description = "Elastic IP — stable across restarts"
  value       = aws_eip.app.public_ip
}

output "public_dns" {
  value = aws_eip.app.public_dns
}
